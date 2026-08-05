package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maktaba.app.ui.theme.CreamBackgroundLight
import com.maktaba.app.ui.theme.InkBrown
import com.maktaba.app.ui.theme.SerifDisplay

@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CreamBackgroundLight)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = InkBrown)
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = InkBrown,
            fontFamily = SerifDisplay,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (trailing != null) {
            trailing()
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}
