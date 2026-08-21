package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme

enum class ButtonTone { PRIMARY, SECONDARY, SUCCESS, DESTRUCTIVE }

private val ButtonHeight = 56.dp

@Composable
fun MaktabaButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    tone: ButtonTone = ButtonTone.PRIMARY,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    val active = enabled && !loading
    val shape = MaktabaShapes.pill
    val fill = when (tone) {
        ButtonTone.PRIMARY -> Brush.verticalGradient(listOf(colors.primary, colors.primaryDark))
        ButtonTone.SECONDARY -> Brush.verticalGradient(listOf(colors.secondary, colors.secondary))
        ButtonTone.SUCCESS -> Brush.verticalGradient(listOf(colors.accent, colors.accent))
        ButtonTone.DESTRUCTIVE -> Brush.verticalGradient(listOf(colors.danger, colors.danger))
    }
    val content = when (tone) {
        ButtonTone.PRIMARY -> colors.onPrimary
        ButtonTone.SECONDARY -> colors.onSecondary
        ButtonTone.SUCCESS -> colors.onAccent
        ButtonTone.DESTRUCTIVE -> colors.onDanger
    }
    val disabledFill = Brush.verticalGradient(
        listOf(colors.inkMuted.copy(alpha = 0.45f), colors.inkMuted.copy(alpha = 0.55f))
    )
    Button(
        onClick = onClick,
        enabled = active,
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(shape)
            .background(if (active) fill else disabledFill)
            .semantics { if (loading) stateDescription = "Loading" },
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = content,
            disabledContentColor = content.copy(alpha = 0.85f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = content,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            } else if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(8.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (trailingIcon != null && !loading) {
                Spacer(Modifier.width(8.dp))
                trailingIcon()
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) = MaktabaButton(text, modifier, enabled, loading, ButtonTone.PRIMARY, leadingIcon, trailingIcon, onClick)

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) = MaktabaButton(text, modifier, enabled, loading, ButtonTone.SECONDARY, leadingIcon, trailingIcon, onClick)

@Composable
fun GreenButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) = MaktabaButton(text, modifier, enabled, loading, ButtonTone.SUCCESS, leadingIcon, null, onClick)

@Composable
fun DestructiveButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit = {}
) = MaktabaButton(text, modifier, enabled, loading, ButtonTone.DESTRUCTIVE, null, null, onClick)

@Composable
fun OutlinedPillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = ButtonHeight),
        shape = MaktabaShapes.pill
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.ink
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = colors.ink, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Low-emphasis action. Used where a screen previously stacked three equal-weight pills and
 * gave the destructive option the same visual authority as the way out.
 */
@Composable
fun TextActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
    ) {
        Text(
            text,
            color = if (destructive) colors.danger else colors.inkSoft,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
