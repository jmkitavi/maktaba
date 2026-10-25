package com.maktaba.app.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.maktaba.app.data.OwnedBookDraft
import com.maktaba.app.journeys.JourneyCapture
import com.maktaba.app.journeys.JourneyFixtures
import com.maktaba.app.ui.theme.BookHavenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SoulAccountEditTest {
    @get:Rule val compose = createComposeRule()

    @Test fun passwordVisibilityAndAccountNavigationAreRealActions() {
        var state by mutableStateOf(AuthUiState(password = "not-a-real-password"))
        var destination = AuthPage.SIGN_IN
        compose.setContent {
            BookHavenTheme {
                AuthContent(state, {}, { _, _ -> }, { destination = it },
                    { state = state.copy(passwordVisible = !state.passwordVisible) }, {})
            }
        }
        compose.onNodeWithText("Show password").performClick()
        compose.onNodeWithText("Hide password").assertExists()
        assertTrue(state.passwordVisible)
        compose.onNodeWithText("Forgot password?").performScrollTo().performClick()
        assertEquals(AuthPage.RESET, destination)
    }

    @Test fun failedSaveRetainsDraftAndUnsavedChoiceDoesNotDeleteBook() {
        val book = JourneyFixtures.books.first()
        val draft = OwnedBookDraft.from(book).copy(title = "Retained edition")
        var page by mutableStateOf(BookEditPage.FORM)
        var discarded = false
        compose.setContent {
            BookHavenTheme {
                EditOwnedBookContent(book, draft, page, false, false, "Couldn't save. Your draft is still here.",
                    { page = BookEditPage.DISCARD }, {}, {}, { page = BookEditPage.FORM }, { discarded = true }, {})
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Retained edition")).assertExists()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNode(hasSetTextAction() and hasText("Retained edition")).assertExists()
        assertEquals(false, discarded)
    }

    @Test fun captureSoulAccountAndEditStates() {
        JourneyCapture.prepareWindow()
        var reference by mutableStateOf("companion-56")
        val book = JourneyFixtures.books.first()
        val draft = OwnedBookDraft.from(book)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, JourneyCapture.fontScale)) {
                BookHavenTheme(darkTheme = JourneyCapture.darkTheme) {
                    key(reference) {
                        when (reference) {
                            "companion-56", "companion-57", "companion-58" ->
                                OnboardingContent(reference.substringAfter('-').toInt() - 56, {}, {})
                            "companion-63" -> LogoutContent(false, {}, {})
                            "companion-64", "companion-65", "companion-66", "book-edit-retry" ->
                                EditOwnedBookContent(book, if (reference == "companion-64") draft else
                                    draft.copy(title = "${book.title} · Demo edition"),
                                    when (reference) {
                                        "companion-65" -> BookEditPage.DISCARD
                                        "companion-66" -> BookEditPage.SAVED
                                        else -> BookEditPage.FORM
                                    }, false, false,
                                    if (reference == "book-edit-retry") "Couldn't save. Your draft is still here. Try again." else null,
                                    {}, {}, {}, {}, {}, {})
                            else -> {
                                val state = when (reference) {
                                    "companion-60" -> AuthUiState(page = AuthPage.REGISTER)
                                    "companion-61" -> AuthUiState(page = AuthPage.RESET)
                                    "companion-62" -> AuthUiState(page = AuthPage.RESET_SENT, email = "alex@example.test")
                                    "sign-in-validation" -> AuthUiState(fieldErrors = authValidation(AuthUiState()))
                                    "sign-in-retry" -> AuthUiState(email = "alex@example.test", password = "fictional-only",
                                        error = "Couldn't sign in. Check your connection and try again.")
                                    else -> AuthUiState()
                                }
                                AuthContent(state, {}, { _, _ -> }, {}, {}, {})
                            }
                        }
                    }
                }
            }
        }
        for (key in (56..66).map { "companion-$it" } +
            listOf("sign-in-validation", "sign-in-retry", "book-edit-retry")) {
            compose.runOnIdle { reference = key }
            JourneyCapture.save(compose, key)
        }
    }
}
