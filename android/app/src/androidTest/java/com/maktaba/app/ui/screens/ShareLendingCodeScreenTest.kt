package com.maktaba.app.ui.screens

import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.InviteUnavailableException
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoanInviteCode
import com.maktaba.app.data.PendingLoanInvite
import com.maktaba.app.ui.theme.BookHavenTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ShareLendingCodeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun uncachedInvitationCanRemainLoadingWithoutCrashing() {
        composeRule.setContent {
            BookHavenTheme {
                ShareLendingCodeScreen(
                    navController = rememberNavController(),
                    inviteId = "uncached_test_invitation",
                    loadInvite = { awaitCancellation() }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Loading this invitation").assertIsDisplayed()
    }

    @Test
    fun missingBookWaitsForInitialLibrarySnapshotInsteadOfBecomingUnavailable() {
        val loaded = mutableStateOf(false)
        composeRule.setContent {
            BookHavenTheme {
                ShareInviteBookStatusContent(
                    libraryLoaded = loaded.value, error = null,
                    onBack = {}, onLibrary = {}, onRetry = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("Loading this book").assertIsDisplayed()
        composeRule.onNodeWithText("Book unavailable").assertDoesNotExist()
        composeRule.runOnIdle { loaded.value = true }
        composeRule.onNodeWithText("Book unavailable").assertIsDisplayed()
    }

    @Test
    fun usedInvitationIsUnavailableRatherThanRetryableNetworkFailure() {
        composeRule.setContent {
            BookHavenTheme {
                ShareLendingCodeScreen(rememberNavController(), "used_test",
                    loadInvite = { throw InviteUnavailableException("accepted", "This invitation was already accepted.") })
            }
        }
        composeRule.onNodeWithText("Invitation already used").assertIsDisplayed()
        composeRule.onNodeWithText("Copy Code").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun transientLoadFailureOffersRetryAndReturnsToLoading() {
        var calls = 0
        composeRule.setContent {
            BookHavenTheme {
                ShareLendingCodeScreen(rememberNavController(), "retry_test", loadInvite = {
                    calls++
                    if (calls == 1) throw IllegalStateException("Connection unavailable.")
                    awaitCancellation()
                })
            }
        }
        composeRule.onNodeWithText("Connection unavailable.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.onNodeWithContentDescription("Loading this invitation").assertIsDisplayed()
    }

    @Test
    fun invitationAndOutgoingShareUseCanonicalDetailsInsteadOfPrivateEdits() {
        val originalCopies = LibraryRepository.books.toList()
        val originalCatalogue = LibraryRepository.maktabaCollection.toList()
        val canonical = Book("catalogue", "Catalogue title", "Catalogue author", 0, BookStatus.OWNED)
        val privateCopy = canonical.copy(id = "copy", title = "Private title", author = "Private author")
        val invite = PendingLoanInvite("invite", privateCopy.id,
            requireNotNull(LoanInviteCode.parse("ABCD-1234")),
            Instant.now().plusSeconds(7200), Instant.now().plusSeconds(3600))
        var sharedText: String? = null
        try {
            LibraryRepository.books.clear()
            LibraryRepository.books.add(privateCopy)
            LibraryRepository.maktabaCollection.clear()
            LibraryRepository.maktabaCollection.add(canonical)
            composeRule.setContent {
                val context = LocalContext.current
                val sharingContext = object : ContextWrapper(context) {
                    override fun startActivity(intent: Intent) {
                        @Suppress("DEPRECATION")
                        val shared = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        sharedText = requireNotNull(shared).getStringExtra(Intent.EXTRA_TEXT)
                    }
                }
                CompositionLocalProvider(LocalContext provides sharingContext) {
                    BookHavenTheme {
                        ShareLendingCodeScreen(rememberNavController(), invite.id, loadInvite = { invite })
                    }
                }
            }
            composeRule.onNodeWithText(canonical.title).assertIsDisplayed()
            composeRule.onNodeWithText(privateCopy.title).assertDoesNotExist()
            composeRule.onNodeWithText("Share Link").performClick()
            composeRule.runOnIdle {
                val text = requireNotNull(sharedText)
                assertTrue(text.contains(canonical.title))
                assertTrue(text.contains(lendingLinkFor(invite.code.value)))
                assertFalse(text.contains(privateCopy.title))
                assertFalse(text.contains(privateCopy.author))
            }
        } finally {
            composeRule.runOnIdle {
                LibraryRepository.books.clear()
                LibraryRepository.books.addAll(originalCopies)
                LibraryRepository.maktabaCollection.clear()
                LibraryRepository.maktabaCollection.addAll(originalCatalogue)
            }
        }
    }
}
