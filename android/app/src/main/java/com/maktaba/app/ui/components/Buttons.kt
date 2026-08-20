package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maktaba.app.ui.theme.InkBrown
import com.maktaba.app.ui.theme.MutedText
import com.maktaba.app.ui.theme.OliveGreen
import com.maktaba.app.ui.theme.TaupeButton
import com.maktaba.app.ui.theme.WoodBrown
import com.maktaba.app.ui.theme.WoodBrownDark

enum class ButtonTone { PRIMARY, SECONDARY, SUCCESS, DESTRUCTIVE }

@Composable
fun MaktabaButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    tone: ButtonTone = ButtonTone.PRIMARY,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val active = enabled && !loading
    val shape = RoundedCornerShape(28.dp)
    val background = when (tone) {
        ButtonTone.PRIMARY -> Brush.verticalGradient(listOf(WoodBrown, WoodBrownDark))
        ButtonTone.SECONDARY -> Brush.verticalGradient(listOf(TaupeButton, TaupeButton))
        ButtonTone.SUCCESS -> Brush.verticalGradient(listOf(OliveGreen, OliveGreen))
        ButtonTone.DESTRUCTIVE -> Brush.verticalGradient(listOf(Color(0xFFB3261E), Color(0xFF8C1D18)))
    }
    Button(
        onClick = onClick,
        enabled = active,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(
                if (active) background
                else Brush.verticalGradient(listOf(MutedText.copy(alpha = 0.55f), MutedText.copy(alpha = 0.65f)))
            )
            .semantics { if (loading) stateDescription = "Loading" },
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.8f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                leadingIcon?.invoke()
            }
            if (loading || leadingIcon != null) Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
) = MaktabaButton(text, modifier, enabled, loading, ButtonTone.PRIMARY, leadingIcon, onClick)

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) = MaktabaButton(text, modifier, enabled, loading, ButtonTone.SECONDARY, leadingIcon, onClick)

@Composable
fun GreenButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) = MaktabaButton(text, modifier, enabled, loading, ButtonTone.SUCCESS, leadingIcon, onClick)

@Composable
fun DestructiveButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit = {}
) = MaktabaButton(text, modifier, enabled, loading, ButtonTone.DESTRUCTIVE, onClick = onClick)

@Composable
fun OutlinedPillButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit = {}
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = InkBrown)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = InkBrown, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
