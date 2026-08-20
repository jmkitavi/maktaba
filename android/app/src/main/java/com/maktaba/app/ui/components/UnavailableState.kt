package com.maktaba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maktaba.app.ui.theme.CreamBackground
import com.maktaba.app.ui.theme.InkBrown
import com.maktaba.app.ui.theme.MutedText
import com.maktaba.app.ui.theme.SerifDisplay

@Composable
fun UnavailableState(
    title: String,
    message: String,
    onBack: () -> Unit,
    onLibrary: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(CreamBackground).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = InkBrown,
            fontFamily = SerifDisplay,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(message, color = MutedText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = "Go Back", onClick = onBack)
        Spacer(Modifier.height(10.dp))
        OutlinedPillButton(text = "Return to My Library", onClick = onLibrary)
    }
}
