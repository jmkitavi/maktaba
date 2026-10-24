package com.maktaba.app.journeys

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object JourneyCapture {
    fun prepareWindow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComponentActivity>().single()
            activity.enableEdgeToEdge()
        }
    }

    val darkTheme: Boolean
        get() = InstrumentationRegistry.getArguments().getString("captureTheme", "light") == "dark"
    val width: Int
        get() = InstrumentationRegistry.getArguments().getString("captureWidth", "360").toInt().also {
            require(it in setOf(320, 360, 390, 430)) { "Unsupported capture width: $it" }
        }
    val fontScale: Float
        get() = InstrumentationRegistry.getArguments().getString("captureFontScale", "1.0").toFloat().also {
            require(it.isFinite() && it > 0f) { "Invalid capture font scale" }
        }

    fun save(rule: ComposeContentTestRule, reference: String) {
        require(reference.matches(Regex("[A-Za-z0-9_-]+")))
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "journey-captures")
        check(directory.isDirectory || directory.mkdirs()) { "Could not create capture directory." }
        val theme = if (darkTheme) "dark" else "light"
        File(directory, "$reference-$theme-${width}dp-font$fontScale.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not save $reference." }
        }
        recordGeometry(rule, reference)
    }

    fun recordGeometry(
        rule: ComposeContentTestRule,
        reference: String,
        theme: String = if (darkTheme) "dark" else "light",
        captureWidth: Int = width,
        captureFontScale: Float = fontScale
    ) {
        val nodes = rule.onAllNodes(
            SemanticsMatcher("Book cover frames") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("cover:") == true
            }, useUnmergedTree = true
        ).fetchSemanticsNodes()
        val covers = JSONArray()
        for (node in nodes) {
            val bounds = node.boundsInRoot
            covers.put(JSONObject().apply {
                put("id", node.config[SemanticsProperties.TestTag].removePrefix("cover:"))
                put("x", node.positionInRoot.x); put("y", node.positionInRoot.y)
                put("width", node.size.width); put("height", node.size.height)
                put("visibleWidth", bounds.width); put("visibleHeight", bounds.height)
            })
        }
        val textNodes = rule.onAllNodes(
            SemanticsMatcher("Text geometry") { it.config.contains(SemanticsProperties.Text) },
            useUnmergedTree = true
        ).fetchSemanticsNodes()
        val texts = JSONArray()
        val bitmapBounds = rule.onRoot().fetchSemanticsNode().boundsInRoot
        var systemInsets: Insets? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComponentActivity>().single()
            systemInsets = checkNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            for (node in textNodes) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                texts.put(JSONObject().apply {
                    put("text", node.config[SemanticsProperties.Text].joinToString(" ") { it.text })
                    put("x", node.positionInRoot.x); put("y", node.positionInRoot.y)
                    put("width", node.size.width); put("height", node.size.height)
                    put("heading", node.config.contains(SemanticsProperties.Heading))
                    layouts.firstOrNull()?.let { layout ->
                        put("lineCount", layout.lineCount)
                        put("overflowWidth", layout.didOverflowWidth); put("overflowHeight", layout.didOverflowHeight)
                        put("ellipsized", (0 until layout.lineCount).any(layout::isLineEllipsized))
                        if (layout.layoutInput.style.fontSize.isSp) put("fontSizeSp", layout.layoutInput.style.fontSize.value)
                    }
                })
            }
        }
        val insets = checkNotNull(systemInsets)
        val record = JSONObject().apply {
            put("reference", reference); put("theme", theme); put("contentWidthDp", captureWidth)
            put("fontScale", captureFontScale); put("widthPx", bitmapBounds.width); put("heightPx", bitmapBounds.height)
            put("systemInsetsPx", JSONObject().apply {
                put("top", insets.top); put("bottom", insets.bottom)
                put("left", insets.left); put("right", insets.right)
            })
            put("covers", covers)
            put("texts", texts)
        }
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "journey-captures")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "$reference-$theme-${captureWidth}dp-font$captureFontScale.geometry.json").writeText(record.toString(2))
    }
}
