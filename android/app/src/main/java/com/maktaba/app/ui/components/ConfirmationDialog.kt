package com.maktaba.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.maktaba.app.ui.theme.InkBrown

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
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(title, color = InkBrown) },
        text = { Text(message, color = InkBrown) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !loading) {
                Text(
                    if (loading) "Please wait..." else confirmLabel,
                    color = if (destructive) Color(0xFFB3261E) else InkBrown
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("Cancel", color = InkBrown)
            }
        }
    )
}
