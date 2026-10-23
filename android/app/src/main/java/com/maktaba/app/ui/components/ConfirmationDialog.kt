package com.maktaba.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.maktaba.app.ui.theme.MaktabaTheme

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    loading: Boolean = false,
    destructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaktabaTheme.colors
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor = colors.surface,
        titleContentColor = colors.ink,
        textContentColor = colors.inkSoft,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !loading) {
                Text(
                    if (loading) "Working..." else confirmLabel,
                    color = if (destructive) colors.danger else colors.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("Cancel", color = colors.inkSoft, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
