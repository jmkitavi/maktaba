package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.maktaba.app.ui.theme.MaktabaTheme

@Composable
fun UnavailableState(
    title: String,
    message: String,
    onBack: () -> Unit,
    onLibrary: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = colors.ink,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            message,
            color = colors.inkMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(spacing.lg))
        if (primaryLabel != null && onPrimary != null) {
            PrimaryButton(text = primaryLabel, onClick = onPrimary)
            Spacer(Modifier.height(spacing.sm))
            OutlinedPillButton(text = "Back to my library", onClick = onLibrary)
        } else {
            PrimaryButton(text = "Go back", onClick = onBack)
            Spacer(Modifier.height(spacing.sm))
            OutlinedPillButton(text = "Back to my library", onClick = onLibrary)
        }
    }
}
