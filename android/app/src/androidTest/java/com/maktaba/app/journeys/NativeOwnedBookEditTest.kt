package com.maktaba.app.journeys

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.maktaba.app.BuildConfig
import com.maktaba.app.MainActivity
import com.maktaba.app.data.CollectionsRepository
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class NativeOwnedBookEditTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun privateEditSurvivesRecreationAndServerRoundTrip() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runNativeOwnedEdit") == "true")
        check(BuildConfig.USE_FIREBASE_EMULATORS) { "Only local Firebase services are permitted." }
        FirebaseSession.currentUser
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val id = "soul-edit-${UUID.randomUUID()}"
        val user = checkNotNull(auth.createUserWithEmailAndPassword("$id@example.test", UUID.randomUUID().toString()).await().user)
        var scenario: ActivityScenario<MainActivity>? = null
        var createdCopy = false
        try {
            val catalogue = db.collection("catalogBooks").limit(1).get(Source.SERVER).await().documents.single()
            val canonicalTitle = catalogue.getString("title")
            db.collection("userBooks").document(id).set(mapOf(
                "ownerId" to user.uid, "catalogBookId" to catalogue.id, "status" to "AVAILABLE",
                "createdAt" to Timestamp.now(), "updatedAt" to Timestamp.now()
            )).await()
            createdCopy = true
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("route", Routes.EditOwnedBook.createRoute(id)))
            compose.waitUntil(30_000) { LibraryRepository.books.any { it.id == id } }
            compose.onNode(hasContentDescription("Title") and hasSetTextAction()).performTextReplacement("My private edition")
            scenario.recreate()
            compose.waitUntil(30_000) { LibraryRepository.books.any { it.id == id } }
            compose.onNode(hasContentDescription("Title") and hasSetTextAction() and hasText("My private edition")).assertExists()
            compose.onNodeWithText("Save changes").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("A few details, made yours.").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("My private edition", db.collection("userBooks").document(id).get(Source.SERVER).await().getString("privateDetails.title"))
            assertEquals(canonicalTitle, db.collection("catalogBooks").document(catalogue.id).get(Source.SERVER).await().getString("title"))
            compose.onNodeWithText("View the edited book").performScrollTo().performClick()
            compose.waitUntil(30_000) { LibraryRepository.books.any { it.id == id && it.title == "My private edition" } }
        } finally {
            try {
                if (createdCopy) {
                    FirebaseFunctions.getInstance().getHttpsCallable("removeBookFromLibrary")
                        .call(mapOf("copyId" to id)).await()
                }
            } finally {
                scenario?.close()
                withContext(Dispatchers.Main) {
                    LibraryRepository.stop()
                    CollectionsRepository.stop()
                }
                try {
                    user.delete().await()
                } finally {
                    auth.signOut()
                }
            }
        }
    }
}
