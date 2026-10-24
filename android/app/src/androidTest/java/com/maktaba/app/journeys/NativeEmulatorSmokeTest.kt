package com.maktaba.app.journeys

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctionsException
import com.maktaba.app.BuildConfig
import com.maktaba.app.MainActivity
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.CollectionsRepository
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanInviteCode
import com.maktaba.app.data.LoanStatus
import com.maktaba.app.nav.Routes
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class NativeEmulatorSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun authenticatedHandoffAndTwoPartyReturnUseRealServices() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runNativeEmulatorSmoke") == "true")
        check(BuildConfig.USE_FIREBASE_EMULATORS) { "Native smoke tests require Firebase emulators." }
        val context = instrumentation.targetContext
        val fixture = JSONObject(File(context.filesDir, "native-local-fixtures.json").readText())
        check(fixture.getBoolean("localOnly"))
        check(fixture.getString("projectId") == "maktaba-2e21d")
        check(fixture.getString("androidHost") == "10.0.2.2")
        check(FirebaseApp.getInstance().options.projectId == fixture.getString("projectId"))
        FirebaseSession.currentUser
        val auth = FirebaseAuth.getInstance()
        val accounts = fixture.getJSONArray("accounts")
        val lender = (0 until accounts.length()).map(accounts::getJSONObject).single { it.getString("role") == "lender" }
        val borrower = (0 until accounts.length()).map(accounts::getJSONObject).single { it.getString("role") == "borrower" }
        val books = fixture.getJSONArray("books")
        val book = (0 until books.length()).map(books::getJSONObject).first { it.optString("coverUrl").isBlank() }
        val copyId = book.getString("copyId")
        val catalogId = book.getString("catalogBookId")
        var scenario: ActivityScenario<MainActivity>? = null

        suspend fun signIn(account: JSONObject) = withContext(Dispatchers.Main.immediate) {
            LibraryRepository.stop()
            CollectionsRepository.stop()
            auth.signOut()
            FirebaseSession.signIn(account.getString("email"), account.getString("password"))
        }
        fun open(route: String) {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("route", route)
            })
            compose.waitForIdle()
        }
        fun waitForText(text: String) {
            try {
                compose.waitUntil(30_000) {
                    compose.onAllNodesWithText(text, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
                }
            } catch (error: ComposeTimeoutException) {
                throw AssertionError("Expected '$text'.\n${compose.onRoot().printToString()}", error)
            }
        }
        fun click(text: String) {
            waitForText(text)
            compose.onNode(hasText(text, ignoreCase = true) and hasClickAction())
                .assertIsDisplayed().assertIsEnabled().performClick()
        }
        try {
            signIn(lender)
            scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).apply {
                putExtra("route", Routes.BookDetail.createRoute(copyId))
            })
            compose.waitUntil(30_000) {
                LibraryRepository.hasLoadedLibrary && LibraryRepository.books.any { it.id == copyId }
            }
            click("Lend This Book")
            click("Generate Lending Code")
            val clipboard = checkNotNull(ContextCompat.getSystemService(context, ClipboardManager::class.java))
            instrumentation.runOnMainSync { clipboard.setPrimaryClip(ClipData.newPlainText("Native smoke", "")) }
            click("Copy Code")
            var copied: String? = null
            compose.waitUntil(15_000) {
                instrumentation.runOnMainSync {
                    copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                }
                LoanInviteCode.parse(copied) != null
            }
            val code = requireNotNull(LoanInviteCode.parse(copied)) { "Copy Code did not copy a real invitation." }

            signIn(borrower)
            compose.waitUntil(30_000) {
                LibraryRepository.hasLoadedLibrary && LibraryRepository.books.none { it.id == copyId }
            }
            open(Routes.ConfirmBorrow.createRoute(code.value))
            click("I Have the Book")
            waitForText("Borrow confirmed")
            compose.waitUntil(30_000) {
                LibraryRepository.books.any { it.catalogId == catalogId && it.status == BookStatus.BORROWED }
            }
            assertEquals(1, LibraryRepository.books.count { it.catalogId == catalogId })
            val resolved = withContext(Dispatchers.Main.immediate) { LibraryRepository.resolveLoanCode(code.value) }
            assertEquals(LoanStatus.ACTIVE, LibraryRepository.activeLoanFor(resolved)?.status)
            assertEquals(BookFormat.PHYSICAL, LibraryRepository.bookById(resolved)?.format)

            signIn(lender)
            compose.waitUntil(30_000) {
                LibraryRepository.books.any { it.id == copyId && it.status == BookStatus.LENT_OUT } &&
                    LibraryRepository.activeLoanFor(copyId)?.status == LoanStatus.ACTIVE
            }
            val cooldown = LibraryRepository.sendReminder(copyId)
            assertNotNull(cooldown)
            assertTrue(requireNotNull(cooldown).isAfter(Instant.now().plusSeconds(5 * 60 * 60)))
            var repeatedReminderRejected = false
            try {
                LibraryRepository.sendReminder(copyId)
            } catch (error: FirebaseFunctionsException) {
                assertEquals(FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED, error.code)
                repeatedReminderRejected = true
            }
            assertTrue(repeatedReminderRejected)
            LibraryRepository.confirmReturn(copyId)
            compose.waitUntil(30_000) {
                LibraryRepository.activeLoanFor(copyId)?.status == LoanStatus.RETURN_REQUESTED
            }
            var initiatorRejected = false
            try {
                LibraryRepository.confirmReturn(copyId)
            } catch (error: IllegalStateException) {
                assertTrue(error.message.orEmpty().contains("other participant"))
                initiatorRejected = true
            }
            assertTrue(initiatorRejected)

            signIn(borrower)
            compose.waitUntil(30_000) {
                LibraryRepository.loanFor(copyId)?.status == LoanStatus.RETURN_REQUESTED
            }
            open(Routes.ReturnConfirmation.createRoute(copyId))
            click("Confirm the Return")
            click("Confirm Return")
            compose.waitUntil(30_000) {
                LibraryRepository.loanFor(copyId)?.status == LoanStatus.RETURNED &&
                    LibraryRepository.books.none { it.catalogId == catalogId }
            }
            assertFalse(LibraryRepository.books.any { it.catalogId == catalogId })
            val returned = withContext(Dispatchers.Main.immediate) { LibraryRepository.resolveLoanCode(code.value) }
            assertEquals(LoanStatus.RETURNED, LibraryRepository.activeLoanFor(returned)?.status)
            assertFalse(LibraryRepository.books.any { it.catalogId == catalogId })
            signIn(lender)
            compose.waitUntil(30_000) {
                LibraryRepository.books.any { it.id == copyId && it.status == BookStatus.OWNED } &&
                    LibraryRepository.activeLoanFor(copyId) == null
            }
            assertEquals(BookFormat.PHYSICAL, LibraryRepository.bookById(copyId)?.format)
        } finally {
            scenario?.close()
            withContext(Dispatchers.Main.immediate) {
                LibraryRepository.stop()
                CollectionsRepository.stop()
                auth.signOut()
            }
        }
    }
}
