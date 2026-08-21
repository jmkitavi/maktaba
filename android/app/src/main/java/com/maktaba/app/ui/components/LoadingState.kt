package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.maktaba.app.ui.theme.MaktabaTheme

/**
 * Shown while a screen's data is still in flight. Screens that resolve a book or a loan
 * used to fall straight through to "unavailable" during that window, telling the user
 * something was missing when it had simply not arrived yet.
 */
@Composable
fun LoadingState(label: String = "Loading") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaktabaTheme.colors.background)
            .systemBarsPadding()
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaktabaTheme.colors.primary)
    }
}
