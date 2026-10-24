package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maktaba.app.ui.theme.MaktabaTheme

@Composable
fun ScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    branded: Boolean = false,
    illustrationRes: Int? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(
                start = if (onBack == null) 20.dp else 14.dp,
                end = 16.dp,
                top = if (branded && illustrationRes == null) 22.dp else if (branded) 14.dp else 8.dp,
                bottom = if (branded && illustrationRes == null) 17.dp else if (branded) 14.dp else if (onBack != null) 5.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.ink
                )
            }
        }
        if (title.isBlank()) {
            Spacer(Modifier.weight(1f))
        } else {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (illustrationRes != null) 5.dp else 7.dp)
            ) {
                if (branded) {
                    Text(
                        "BOOK HAVEN",
                        color = colors.primary,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp, lineHeight = 10.sp, letterSpacing = 1.8.sp
                        )
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    illustrationRes?.let {
                        Image(
                            painter = painterResource(it),
                            contentDescription = null,
                            modifier = Modifier.width(36.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Text(
                        title,
                        modifier = Modifier.weight(1f).semantics { heading() },
                        color = colors.ink,
                        style = when {
                            !branded -> MaterialTheme.typography.headlineMedium
                            illustrationRes != null -> MaterialTheme.typography.headlineLarge
                            else -> MaterialTheme.typography.displayMedium
                        }.copy(letterSpacing = (-0.8).sp),
                        textAlign = TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
