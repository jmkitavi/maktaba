package com.maktaba.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.navigation.compose.rememberNavController
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
}
