package com.maktaba.app.ui.screens

import org.junit.Assert.*
import org.junit.Test

class AuthValidationTest {
    @Test fun signInAndRegistrationShowRelevantFieldFeedback() {
        assertEquals(setOf("Email", "Password"), authValidation(AuthUiState()).keys)
        assertEquals(setOf("Name", "Password"), authValidation(
            AuthUiState(page = AuthPage.REGISTER, email = "reader@example.test", password = "short")).keys)
        assertTrue(authValidation(AuthUiState(email = " reader@example.test ", password = "valid-password")).isEmpty())
    }

    @Test fun resetDoesNotRequirePasswordAndInvalidEmailIsRejected() {
        assertTrue(authValidation(AuthUiState(page = AuthPage.RESET, email = "reader@example.test")).isEmpty())
        assertEquals(setOf("Email"), authValidation(AuthUiState(page = AuthPage.RESET, email = "reader@")).keys)
    }
}
