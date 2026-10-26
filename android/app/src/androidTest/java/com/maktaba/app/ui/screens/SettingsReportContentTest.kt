package com.maktaba.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.maktaba.app.data.AppAppearance
import com.maktaba.app.data.DevicePreferences
import com.maktaba.app.data.LibraryReportReason
import com.maktaba.app.journeys.JourneyCapture
import com.maktaba.app.ui.theme.BookHavenTheme
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SettingsReportContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun reportRequiresReasonAndBoundedDetailsBeforeReview() {
        var state by mutableStateOf(ReportUiState("Rowan’s Shelf"))
        var reviewed = false
        compose.setContent {
            BookHavenTheme {
                ReportLibraryContent(state, {}, { state = state.copy(reason = it) }, {}, { reviewed = true }, {})
            }
        }
        compose.onNodeWithText("Review report").assertIsNotEnabled()
        compose.onNodeWithText("Spam or misleading name").performClick()
        compose.onNodeWithText("Review report").assertIsEnabled().performClick()
        assertEquals(true, reviewed)
        compose.runOnIdle { state = state.copy(details = "x".repeat(501)) }
        compose.onNodeWithText("Review report").assertIsNotEnabled()
    }

    @Test
    fun appearancePersistsAfterStoreReinitialization() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DevicePreferences.initialize(context)
        val original = DevicePreferences.appearance
        try {
            DevicePreferences.setAppearance(context, AppAppearance.DARK)
            DevicePreferences.initialize(context)
            assertEquals(AppAppearance.DARK, DevicePreferences.appearance)
            DevicePreferences.setAppearance(context, AppAppearance.LIGHT)
            DevicePreferences.initialize(context)
            assertEquals(AppAppearance.LIGHT, DevicePreferences.appearance)
        } finally {
            DevicePreferences.setAppearance(context, original)
        }
    }

    @Test
    fun reportReviewStaysAboveTheNativeKeyboard() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyNativeIme") == "true")
        JourneyCapture.prepareWindow()
        var state by mutableStateOf(ReportUiState("Rowan’s Shelf", reason = LibraryReportReason.SPAM))
        var reviewed = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, JourneyCapture.fontScale)) {
                BookHavenTheme {
                    ReportLibraryContent(
                        state, {}, {}, { state = state.copy(details = it) }, { reviewed = true }, {}
                    )
                }
            }
        }
        compose.onNode(hasSetTextAction()).performClick().performTextInput("A local keyboard-clearance check.")
        fun keyboardInset(): Int {
            var bottom = 0
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).single()
                bottom = checkNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
                    .getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
            return bottom
        }
        compose.waitUntil(10_000) { keyboardInset() > 0 }
        val review = compose.onNodeWithText("Review report").performScrollTo().assertIsEnabled()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.filesDir, "journey-captures")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "native-keyboard-device.png").outputStream().use {
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        val visibleBottom = minOf(
            compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom,
            (screenshot.height - keyboardInset()).toFloat()
        )
        screenshot.recycle()
        assertTrue("Review action must be above the visible keyboard", review.fetchSemanticsNode().boundsInRoot.bottom <= visibleBottom + 1)
        JourneyCapture.save(compose, "report-keyboard")
        review.performClick()
        assertTrue(reviewed)
    }

    @Test
    fun captureApprovedSettingsAndReportStates() {
        JourneyCapture.prepareWindow()
        var reference by mutableStateOf("companion-27")
        val dark = JourneyCapture.darkTheme
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, JourneyCapture.fontScale)) {
                BookHavenTheme(darkTheme = dark) {
                    key(reference) {
                        when (reference) {
                            "companion-27" -> SettingsContent(SettingsUiState(), {}, {}, {})
                            "companion-28" -> SettingsContent(SettingsUiState(page = SettingsPage.APPEARANCE), {}, {}, {})
                            "companion-29" -> SettingsContent(
                                SettingsUiState(page = SettingsPage.NOTIFICATIONS, permission = DeviceNotificationPermission.ALLOWED),
                                {}, {}, {}
                            )
                            "companion-30" -> SettingsContent(SettingsUiState(page = SettingsPage.ABOUT), {}, {}, {})
                            else -> ReportLibraryContent(
                                ReportUiState(
                                    libraryName = "Rowan’s Reading Room",
                                    ownerDisplayName = "Rowan Ellis",
                                    reason = LibraryReportReason.INAPPROPRIATE_NAME.takeIf {
                                        reference != "companion-41"
                                    },
                                    step = when (reference) {
                                        "companion-42" -> ReportStep.REVIEW
                                        "companion-43" -> ReportStep.SUCCESS
                                        else -> ReportStep.FORM
                                    }
                                ), {}, {}, {}, {}, {}
                            )
                        }
                    }
                }
            }
        }
        for (name in listOf("companion-27", "companion-28", "companion-29", "companion-30", "companion-41", "companion-42", "companion-43")) {
            compose.runOnIdle { reference = name }
            val heading = when (name) {
                "companion-27" -> "Settings"
                "companion-28" -> "Appearance"
                "companion-29" -> "Device notifications"
                "companion-30" -> "About"
                "companion-42" -> "Review report"
                "companion-43" -> "Report outcome"
                else -> "Report library"
            }
            compose.onNode(hasText(heading) and SemanticsMatcher("Screen heading") {
                it.config.contains(SemanticsProperties.Heading)
            }).assertExists()
            JourneyCapture.save(compose, name)
        }
    }
}
