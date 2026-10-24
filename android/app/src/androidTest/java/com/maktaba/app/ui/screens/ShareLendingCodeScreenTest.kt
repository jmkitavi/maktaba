package com.maktaba.app.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.InviteUnavailableException
import com.maktaba.app.ui.theme.BookHavenTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test

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
}
