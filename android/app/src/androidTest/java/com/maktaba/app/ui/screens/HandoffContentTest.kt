package com.maktaba.app.ui.screens

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.maktaba.app.data.*
import com.maktaba.app.ui.theme.BookHavenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class HandoffContentTest {
    @get:Rule val compose = createComposeRule()
    private val now = Instant.parse("2026-10-23T12:00:00Z")
    private val book = Book("test-copy", "Test title", "Test author", 0, BookStatus.OWNED,
        format = BookFormat.PHYSICAL)
    private val loan = ActiveLoan("test-invite", book.id, "Alex", LoanRole.BORROWER,
        LoanStatus.PENDING, now.plusSeconds(3600), null, null, null)

    @Test
    fun expiredCodeCannotBeCopiedOrShared() {
        val invite = PendingLoanInvite("test-invite", book.id,
            requireNotNull(LoanInviteCode.parse("ABCD-1234")), now.plusSeconds(7200), now)
        compose.setContent {
            BookHavenTheme {
                ShareLendingCodeContent(ShareLendingCodeState(book, invite, now),
                    {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("Copy Code").assertIsNotEnabled()
        compose.onNodeWithText("Share Link").assertIsNotEnabled()
        compose.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun productionInvitationQrBitmapDecodesTheRealLendingLink() {
        val code = "ABCD-1234"
        val invite = PendingLoanInvite("test-invite", book.id,
            requireNotNull(LoanInviteCode.parse(code)), now.plusSeconds(7200), now.plusSeconds(3600))
        compose.setContent {
            BookHavenTheme {
                ShareLendingCodeContent(ShareLendingCodeState(book, invite, now),
                    {}, {}, {}, {}, {})
            }
        }
        val bitmap = compose.onNodeWithContentDescription("QR code for lending invitation")
            .captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(
            RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        )))
        assertEquals(BarcodeFormat.QR_CODE, decoded.barcodeFormat)
        assertEquals("maktaba://loan?code=$code", decoded.text)
        assertEquals(lendingLinkFor(code), decoded.text)
        assertEquals(code, parseLendingCode(decoded.text))
    }

    @Test
    fun acceptedBorrowShowsExplicitSuccessBeforeActionableNavigation() {
        var confirms = 0
        var opened = 0
        val state = mutableStateOf(ConfirmBorrowState(book, loan, now = now))
        compose.setContent {
            BookHavenTheme {
                ConfirmBorrowContent(state.value, {}, {
                    confirms++
                    state.value = state.value.copy(accepted = true)
                }, { opened++ }, {})
            }
        }
        compose.onNodeWithText("I Have the Book").performClick()
        compose.onNodeWithText("Borrow confirmed").assertExists()
        compose.onNodeWithText("I Have the Book").assertDoesNotExist()
        compose.onNodeWithText("Back to Loans").assertIsEnabled()
        compose.onNodeWithText("Back to Loans").performClick()
        assertEquals(1, confirms)
        assertEquals(1, opened)
    }

    @Test
    fun terminalAcceptanceErrorLeavesReviewButDisablesSecondAcceptance() {
        compose.setContent {
            BookHavenTheme {
                ConfirmBorrowContent(ConfirmBorrowState(book, loan, now = now,
                    error = "This invitation was cancelled.", unavailable = true), {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("I Have the Book").assertIsNotEnabled()
        compose.onNodeWithText("Back to Loans").assertIsEnabled()
        compose.onNodeWithText("This invitation was cancelled.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun scannerFailureLeavesManualCodeAndRetryAvailable() {
        var finds = 0
        compose.setContent {
            BookHavenTheme {
                ScanEnterCodeContent(ScanEnterCodeState("ABCD-1234", error = "Scan cancelled."),
                    {}, {}, {}, { finds++ })
            }
        }
        compose.onNodeWithText("ABCD-1234").assertExists()
        compose.onNodeWithText("Scan QR Code").assertIsEnabled()
        compose.onNodeWithText("Find This Book").performClick()
        assertEquals(1, finds)
    }

    @Test
    fun addFailureRetainsEditedMetadataAndKeepsSaveActionAvailable() {
        compose.setContent {
            BookHavenTheme {
                AddBookContent(
                    AddBookState(step = AddBookStep.DETAILS,
                        draft = AddBookDraft(title = "Retained title", author = "Retained author"),
                        error = "Unable to save. Try again."),
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
                )
            }
        }
        compose.onNodeWithContentDescription("Title (required)").assertTextContains("Retained title")
        compose.onNodeWithContentDescription("Author (required)").assertTextContains("Retained author")
        compose.onNodeWithText("Add to my library").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Unable to save. Try again.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun compactIsbnFieldRetainsInputAndSubmitsFromKeyboard() {
        val state = mutableStateOf(AddBookState(step = AddBookStep.ISBN))
        var lookups = 0
        compose.setContent {
            BookHavenTheme {
                AddBookContent(
                    state.value, {}, {}, { state.value = state.value.copy(isbn = it) },
                    {}, {}, {}, {}, { lookups++ }, {}, {}, {}, {}
                )
            }
        }
        compose.onNodeWithContentDescription("ISBN").performTextInput("9780306406157")
        compose.onNodeWithContentDescription("ISBN").assertTextContains("9780306406157")
        compose.onNodeWithContentDescription("ISBN").performImeAction()
        compose.onNodeWithText("Look up this ISBN").assertIsEnabled()
        assertEquals(1, lookups)
    }

    @Test
    fun metadataHeaderShowsBackAndInvokesItsNavigationCallback() {
        var backs = 0
        compose.setContent {
            BookHavenTheme {
                AddBookContent(
                    AddBookState(step = AddBookStep.DETAILS), { backs++ },
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
                )
            }
        }
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().assertHasClickAction()
            .performClick()
        assertEquals(1, backs)
    }
}
