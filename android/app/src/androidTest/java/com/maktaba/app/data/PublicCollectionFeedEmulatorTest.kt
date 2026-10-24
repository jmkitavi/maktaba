package com.maktaba.app.data

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.maktaba.app.BuildConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicCollectionFeedEmulatorTest {
    @Test
    fun offlineReaderNeverReopensAnOwnerHiddenTitleAfterReconnect() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Requires the explicitly enabled local Firebase smoke environment.",
            BuildConfig.USE_FIREBASE_EMULATORS &&
                InstrumentationRegistry.getArguments().getString("runNativeEmulatorSmoke") == "true")

        val context = instrumentation.targetContext
        val manifest = File(context.filesDir, "native-local-fixtures.json")
        check(manifest.isFile) { "Copy the synthetic native local fixture manifest to app files first." }
        val fixtures = JSONObject(manifest.readText())
        check(fixtures.getBoolean("localOnly"))
        check(fixtures.getString("projectId") == "maktaba-2e21d")
        check(fixtures.getString("androidHost") == "10.0.2.2")
        val accounts = fixtures.getJSONArray("accounts")
        fun account(role: String): JSONObject = (0 until accounts.length())
            .map(accounts::getJSONObject).single { it.getString("role") == role }.also {
                check(listOf("uid", "email", "password", "displayName").all { key -> it.getString(key).isNotBlank() })
            }
        val lender = account("lender")
        val borrower = account("borrower")
        check(lender.getString("uid") != borrower.getString("uid"))
        val books = fixtures.getJSONArray("books")
        val book = (0 until books.length()).map(books::getJSONObject).first {
            it.getString("ownerUid") == lender.getString("uid") && it.getString("copyId").isNotBlank()
        }
        val catalogId = book.getString("catalogBookId").also { check(it.isNotBlank() && '/' !in it) }
        val expectedTitle = book.getString("title").also { check(it.isNotBlank()) }
        val ownerUid = lender.getString("uid")
        val readerUid = borrower.getString("uid")

        // This guard precedes SDK access; FirebaseSession configures every default SDK emulator.
        val defaultApp = FirebaseApp.getInstance()
        check(defaultApp.options.projectId == "maktaba-2e21d")
        FirebaseSession.currentUser
        val readerAuth = FirebaseAuth.getInstance()
        val readerDb = FirebaseFirestore.getInstance()
        val ownerApp = checkNotNull(FirebaseApp.initializeApp(context, defaultApp.options,
            "collections-offline-${UUID.randomUUID()}"))
        ownerApp.setDataCollectionDefaultEnabled(false)
        val ownerAuth = FirebaseAuth.getInstance(ownerApp).apply { useEmulator("10.0.2.2", 9099) }
        val ownerFunctions = FirebaseFunctions.getInstance(ownerApp).apply { useEmulator("10.0.2.2", 5001) }
        val ownerDb = FirebaseFirestore.getInstance(ownerApp).apply { useEmulator("10.0.2.2", 8080) }

        runBlocking {
            var feed: PublicCollectionFeed<SharedLibraryPage>? = null
            var observation: Job? = null
            var disposeSnapshotWrites: (() -> Unit)? = null
            var originalName = "Native local shelf"
            var primaryFailure: Throwable? = null
            val protectHidden = AtomicBoolean(false)
            val reopened = AtomicBoolean(false)
            suspend fun mutate(name: String, payload: Map<String, Any>): Map<*, *> {
                val result = ownerFunctions.getHttpsCallable(name).call(payload).await().getData() as Map<*, *>
                return result["settings"] as Map<*, *>
            }
            suspend fun awaitPublication(epoch: Long) = withTimeout(60_000) {
                while (true) {
                    val gate = ownerDb.collection("publicLibraryState").document(ownerUid).get(Source.SERVER).await()
                    if (gate.getBoolean("published") == true && gate.getLong("publicationEpoch") == epoch) break
                    delay(150)
                }
            }
            suspend fun awaitFeed(predicate: (LoadState<SharedLibraryPage>) -> Boolean): LoadState<SharedLibraryPage> =
                withTimeout(60_000) {
                    var state = withContext(Dispatchers.Main) { checkNotNull(feed).state }
                    while (!predicate(state)) {
                        delay(100)
                        state = withContext(Dispatchers.Main) { checkNotNull(feed).state }
                    }
                    state
                }
            try {
                withContext(Dispatchers.Main) { CollectionsRepository.stop() }
                readerAuth.signOut()
                ownerAuth.signInWithEmailAndPassword(lender.getString("email"), lender.getString("password")).await()
                assertEquals(ownerUid, ownerAuth.currentUser?.uid)
                originalName = ownerDb.collection("users").document(ownerUid).get(Source.SERVER).await()
                    .getString("libraryName")?.takeIf { it.trim().length in 1..40 } ?: originalName
                mutate("setTitleVisibility", mapOf("catalogBookId" to catalogId, "hidden" to false))
                val published = mutate("setLibrarySharing", mapOf("libraryShared" to true, "libraryName" to originalName))
                val publishedEpoch = (published["publicationEpoch"] as Number).toLong()
                awaitPublication(publishedEpoch)

                readerAuth.signInWithEmailAndPassword(borrower.getString("email"), borrower.getString("password")).await()
                assertEquals(readerUid, readerAuth.currentUser?.uid)
                readerDb.enableNetwork().await()
                withContext(Dispatchers.Main) {
                    CollectionsRepository.start(readerUid)
                    feed = CollectionsRepository.sharedLibrary(ownerUid)
                }
                val initial = awaitFeed { state -> state is LoadState.Ready &&
                    state.value.books.any { it.catalogBookId == catalogId } }
                check(initial is LoadState.Ready)
                assertEquals(publishedEpoch, initial.value.library.publicationEpoch)
                assertEquals(expectedTitle, initial.value.books.single { it.catalogBookId == catalogId }.title)
                withContext(Dispatchers.Main) {
                    // A headless SDK test has no Compose host to dispatch snapshot apply notifications.
                    Snapshot.sendApplyNotifications()
                    val handler = Handler(Looper.getMainLooper())
                    val writes = Snapshot.registerGlobalWriteObserver {
                        handler.post { Snapshot.sendApplyNotifications() }
                    }
                    disposeSnapshotWrites = { writes.dispose() }
                }
                observation = launch(Dispatchers.Main.immediate) {
                    snapshotFlow { checkNotNull(feed).state }.collect { state ->
                        if (protectHidden.get() && state is LoadState.Ready &&
                            state.value.books.any { it.catalogBookId == catalogId }) reopened.set(true)
                    }
                }

                readerDb.disableNetwork().await()
                awaitFeed { it is LoadState.Error }
                protectHidden.set(true)
                assertFalse(withContext(Dispatchers.Main) { checkNotNull(feed).state is LoadState.Ready })
                val hidden = mutate("setTitleVisibility", mapOf("catalogBookId" to catalogId, "hidden" to true))
                val hiddenEpoch = (hidden["publicationEpoch"] as Number).toLong()
                assertTrue(hiddenEpoch > publishedEpoch)
                awaitPublication(hiddenEpoch)
                assertEquals("Owner publication must not switch the reader's Auth session.",
                    readerUid, readerAuth.currentUser?.uid)
                assertTrue(withContext(Dispatchers.Main) { checkNotNull(feed).state is LoadState.Error })

                readerDb.enableNetwork().await()
                awaitFeed { state -> state is LoadState.Ready &&
                    state.value.library.publicationEpoch == hiddenEpoch &&
                    state.value.books.none { it.catalogBookId == catalogId } }
                repeat(10) {
                    delay(150)
                    withContext(Dispatchers.Main) {
                        val state = checkNotNull(feed).state
                        if (state is LoadState.Ready) {
                            assertEquals(hiddenEpoch, state.value.library.publicationEpoch)
                            assertTrue(state.value.books.none { it.catalogBookId == catalogId })
                        }
                    }
                }
                assertFalse("A cached, newly hidden catalogue association reopened after reconnect.", reopened.get())
                assertEquals(readerUid, readerAuth.currentUser?.uid)
            } catch (error: Throwable) {
                primaryFailure = error
                throw error
            } finally {
                withContext(NonCancellable) {
                    val cleanupFailures = mutableListOf<Throwable>()
                    suspend fun cleanup(action: suspend () -> Unit) {
                        try { withTimeout(20_000) { action() } }
                        catch (error: Throwable) { cleanupFailures += error }
                    }
                    cleanup { observation?.cancelAndJoin() }
                    cleanup { withContext(Dispatchers.Main) { disposeSnapshotWrites?.invoke() } }
                    cleanup { withContext(Dispatchers.Main) { feed?.close(); CollectionsRepository.stop() } }
                    cleanup { readerDb.enableNetwork().await() }
                    if (ownerAuth.currentUser?.uid == ownerUid) {
                        cleanup {
                            val restored = mutate("setTitleVisibility", mapOf("catalogBookId" to catalogId, "hidden" to false))
                            assertFalse((restored["hiddenCatalogIds"] as List<*>).contains(catalogId))
                        }
                        cleanup {
                            val restored = mutate("setLibrarySharing", mapOf("libraryShared" to false, "libraryName" to originalName))
                            assertEquals(false, restored["libraryShared"])
                        }
                    }
                    cleanup { readerAuth.signOut(); ownerAuth.signOut() }
                    cleanup { ownerDb.terminate().await() }
                    cleanup { ownerApp.delete() }
                    cleanupFailures.forEach { error ->
                        if (primaryFailure != null) primaryFailure!!.addSuppressed(error)
                    }
                    if (primaryFailure == null && cleanupFailures.isNotEmpty()) throw cleanupFailures.first()
                }
            }
        }
    }
}
