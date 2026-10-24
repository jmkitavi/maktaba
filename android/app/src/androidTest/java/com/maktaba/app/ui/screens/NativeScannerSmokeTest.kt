package com.maktaba.app.ui.screens

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.maktaba.app.BuildConfig
import com.maktaba.app.MainActivity
import com.maktaba.app.data.CollectionsRepository
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import java.io.File
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class NativeScannerSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun realScannerLaunchAndNativeCancellationOrExplicitUnavailability() = runBlocking {
        assumeTrue("Opt in only against the guarded local fixture environment.",
            InstrumentationRegistry.getArguments().getString("runNativeScannerSmoke") == "true")
        check(BuildConfig.DEBUG && BuildConfig.USE_FIREBASE_EMULATORS) {
            "The native scanner smoke requires the debug Firebase-emulator build."
        }
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "ActivityScenario and Compose test operations require the instrumentation thread."
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manifest = File(context.filesDir, "native-local-fixtures.json")
        check(manifest.isFile) { "The local synthetic-account manifest is required." }
        val fixture = JSONObject(manifest.readText())
        check(fixture.getBoolean("localOnly"))
        check(fixture.getString("projectId") == "maktaba-2e21d")
        check(fixture.getString("androidHost") == "10.0.2.2")
        val accounts = fixture.getJSONArray("accounts")
        val lender = (0 until accounts.length()).map(accounts::getJSONObject)
            .single { it.getString("role") == "lender" }
        check(listOf("uid", "email", "password").all { lender.getString(it).isNotBlank() })

        check(FirebaseApp.getInstance().options.projectId == fixture.getString("projectId"))
        FirebaseSession.currentUser
        val auth = FirebaseAuth.getInstance()
        val automation = instrumentation.uiAutomation
        val activeActivity = AtomicReference("")
        val result = JSONObject().apply {
            put("outcome", "failed")
            put("opticalScanVerified", false)
            put("virtualCameraFeedInjected", false)
            put("scannerActivityObserved", false)
        }
        val scannerUnavailable = "The camera scanner could not open. Try again or enter the code below."
        val scanCancelled = "Scan cancelled. You can try again or enter the code below."
        var scenario: ActivityScenario<MainActivity>? = null
        var verified = false
        fun hasFeedback(text: String): Boolean =
            compose.onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

        fun sanitized(text: String): String {
            var value = text
            for (key in listOf("email", "password", "uid")) {
                value = value.replace(lender.getString(key), "[redacted]")
            }
            return value.take(220)
        }
        fun recordDiagnostics() {
            val root = automation.rootInActiveWindow
            if (root != null) {
                try {
                    result.put("activePackageBeforeTeardown", root.packageName?.toString().orEmpty())
                    result.put("activeUiTextBeforeTeardown", JSONArray(accessibleText(root).map(::sanitized)))
                } finally {
                    root.recycle()
                }
            } else {
                result.put("activePackageBeforeTeardown", JSONObject.NULL)
            }
            result.put("lastActivityClassBeforeTeardown", activeActivity.get())
            val nodes = compose.onAllNodes(SemanticsMatcher("Diagnostic nodes") { true },
                useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)
            result.put("composeSemanticsBeforeTeardown", JSONArray(nodes.take(70).map { node ->
                JSONObject().apply {
                    put("text", JSONArray(node.config.getOrNull(SemanticsProperties.Text)
                        .orEmpty().map { sanitized(it.text) }))
                    put("contentDescription", JSONArray(node.config.getOrNull(SemanticsProperties.ContentDescription)
                        .orEmpty().map(::sanitized)))
                    put("clickable", node.config.contains(SemanticsActions.OnClick))
                    put("disabled", node.config.contains(SemanticsProperties.Disabled))
                }
            }))
        }
        Closeable {
            var cleaned = false
            try {
                try {
                    if (!verified) recordDiagnostics()
                } finally {
                    try {
                        automation.setOnAccessibilityEventListener(null)
                        scenario?.close()
                    } finally {
                        instrumentation.runOnMainSync {
                            LibraryRepository.stop()
                            CollectionsRepository.stop()
                            auth.signOut()
                        }
                    }
                }
                cleaned = true
            } finally {
                result.put("passed", verified && cleaned)
                val directory = File(context.filesDir, "journey-captures")
                check(directory.isDirectory || directory.mkdirs())
                File(directory, "native-scanner-result.json").writeText(result.toString(2))
            }
        }.use {
            result.put("phase", "camera-enumeration")
            val cameras = checkNotNull(context.getSystemService(CameraManager::class.java)).cameraIdList
            result.put("availableCameraIds", JSONArray(cameras.toList()))
            result.put("phase", "synthetic-lender-sign-in")
            withTimeout(30_000) {
                withContext(Dispatchers.Main.immediate) {
                    LibraryRepository.stop()
                    CollectionsRepository.stop()
                    auth.signOut()
                    FirebaseSession.signIn(lender.getString("email"), lender.getString("password"))
                    assertEquals(lender.getString("uid"), auth.currentUser?.uid)
                }
            }
            result.put("phase", "launch-scan-enter-code")
            scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).apply {
                putExtra("route", Routes.ScanEnterCode.route)
            })
            result.put("phase", "wait-for-scan-action")
            compose.waitUntil(30_000) {
                compose.onAllNodesWithText("Scan QR Code").fetchSemanticsNodes().isNotEmpty()
            }
            automation.setOnAccessibilityEventListener { event ->
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val name = event.className?.toString().orEmpty()
                    if (name.endsWith("Activity")) activeActivity.set(name)
                }
            }
            result.put("phase", "click-real-scan-action")
            compose.onNode(hasText("Scan QR Code") and hasClickAction())
                .assertIsDisplayed().assertIsEnabled().performClick()

            val deadline = SystemClock.elapsedRealtime() + 30_000
            var scannerVisible = false
            var unavailable = false
            result.put("phase", "observe-gms-scanner-or-unavailable-feedback")
            while (SystemClock.elapsedRealtime() < deadline && !scannerVisible && !unavailable) {
                unavailable = hasFeedback(scannerUnavailable)
                val root = automation.rootInActiveWindow
                if (root != null) {
                    try {
                        val packageName = root.packageName?.toString().orEmpty()
                        val text = accessibleText(root)
                        result.put("lastObservedActivePackage", packageName)
                        result.put("lastObservedUiText", JSONArray(text.map(::sanitized)))
                        var resumedActivity = ""
                        instrumentation.runOnMainSync {
                            resumedActivity = ActivityLifecycleMonitorRegistry.getInstance()
                                .getActivitiesInStage(Stage.RESUMED)
                                .joinToString { it.javaClass.name }
                        }
                        val activity = listOf(activeActivity.get(), resumedActivity)
                            .firstOrNull { it.contains("barcode", true) || it.contains("scanner", true) }
                            ?: activeActivity.get()
                        unavailable = unavailable ||
                            packageName == context.packageName && text.contains(scannerUnavailable)
                        val scannerActivity = activity.contains("barcode", ignoreCase = true) ||
                            activity.contains("scanner", ignoreCase = true)
                        scannerVisible = !unavailable &&
                            (packageName == context.packageName || packageName == "com.google.android.gms") &&
                            (scannerActivity || packageName == "com.google.android.gms" &&
                                text.any { it.contains("scan", ignoreCase = true) || it.contains("barcode", ignoreCase = true) })
                        if (scannerVisible) {
                            result.put("scannerActivityObserved", true)
                            result.put("foregroundPackage", packageName)
                            result.put("activityClass", activity)
                            result.put("accessibleScannerLabels", JSONArray(text.take(12).map(::sanitized)))
                        }
                    } finally {
                        root.recycle()
                    }
                }
                if (!scannerVisible && !unavailable) SystemClock.sleep(150)
            }
            assertTrue("No real GMS scanner or explicit unavailable feedback appeared within 30 seconds.",
                scannerVisible || unavailable)
            if (scannerVisible) {
                result.put("phase", "cancel-scanner-with-native-back")
                assertTrue("The scanner appeared but native Back was not supported.",
                    automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                val returnDeadline = SystemClock.elapsedRealtime() + 15_000
                var returned = false
                while (SystemClock.elapsedRealtime() < returnDeadline && !returned) {
                    unavailable = hasFeedback(scannerUnavailable)
                    returned = unavailable || hasFeedback(scanCancelled)
                    val root = automation.rootInActiveWindow
                    if (root != null) {
                        try {
                            if (root.packageName?.toString() == context.packageName) {
                                val text = accessibleText(root)
                                unavailable = unavailable || text.contains(scannerUnavailable)
                                returned = returned || unavailable || text.contains(scanCancelled)
                            }
                        } finally {
                            root.recycle()
                        }
                    }
                    if (!returned) SystemClock.sleep(150)
                }
                assertTrue("Native Back did not return explicit cancellation or unavailable feedback.", returned)
            }
            result.put("phase", "verify-feedback-and-manual-entry-recovery")
            compose.waitForIdle()
            val feedback = if (unavailable) scannerUnavailable else scanCancelled
            compose.onNodeWithText(feedback).assertIsDisplayed()
            result.put("feedback", feedback)
            result.put("feedbackEvidence", "exact-production-compose-text")
            compose.onNodeWithText("Scan QR Code").assertIsEnabled()
            compose.onNodeWithContentDescription("Enter the Code")
                .assertIsDisplayed().performTextInput("ABCD-1234")
            compose.onNodeWithContentDescription("Enter the Code").assertTextContains("ABCD-1234")
            compose.onNodeWithText("Find This Book").assertIsEnabled()
            result.put("outcome", if (unavailable) "explicit-scanner-unavailable" else "launched-and-cancelled")
            result.put("manualEntryRecoveryVerified", true)
            result.put("phase", "completed")
            verified = true
        }
    }

    private fun accessibleText(root: AccessibilityNodeInfo): List<String> {
        val text = mutableListOf<String>()
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            node.text?.toString()?.takeIf(String::isNotBlank)?.let { text.add(it.take(180)) }
            node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let { text.add(it.take(180)) }
            if (depth >= 12 || text.size >= 60) return
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }
        visit(root, 0)
        return text.distinct()
    }
}
