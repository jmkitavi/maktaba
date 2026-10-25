package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.R
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.OutlinedPillButton
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.SoulWelcomeIllustration
import com.maktaba.app.ui.theme.MaktabaTheme

private data class WelcomeSlide(val art: Int, val title: String, val body: String)

private val welcomeSlides = listOf(
    WelcomeSlide(R.drawable.illus_onboarding, "Your shelf, catalogued.",
        "A little order for the books you keep. Scan a barcode or add the details yourself."),
    WelcomeSlide(R.drawable.illus_enter_code, "A story worth sharing.",
        "Choose a return date and share a lending code. The loan begins when your friend has the book and confirms."),
    WelcomeSlide(R.drawable.illus_return_complete, "Back where it belongs.",
        "Gentle reminders help along the way. Both readers confirm the return before a loan closes.")
)

@Composable
fun OnboardingScreen(navController: NavHostController) {
    var step by remember { mutableIntStateOf(0) }
    OnboardingContent(step, onStep = { step = it }, onFinish = {
        navController.navigate(Routes.Auth.route) { launchSingleTop = true }
    })
}

@Composable
internal fun OnboardingContent(step: Int, onStep: (Int) -> Unit, onFinish: () -> Unit) {
    val colors = MaktabaTheme.colors
    val slide = welcomeSlides[step]
    SoulAccountPage("Book Haven", branded = true) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (step == 0) SoulWelcomeIllustration(slide.art,
                Modifier.padding(top = 6.dp).size(256.dp, 220.dp))
            else Image(painterResource(slide.art), null, contentScale = ContentScale.Fit,
                modifier = Modifier.padding(top = 6.dp).size(256.dp, 220.dp))
            Text("A LITTLE MORE SOUL / ${step + 1} OF 3", color = colors.accent,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.5.sp))
            Text(slide.title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 35.sp, letterSpacing = (-0.8).sp),
                color = colors.ink, textAlign = TextAlign.Center)
            Text(slide.body, color = colors.inkMuted, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp))
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.Center) {
                welcomeSlides.indices.forEach { index ->
                    Box(Modifier.size(48.dp).selectable(index == step, role = Role.Tab, onClick = { onStep(index) })
                        .semantics { contentDescription = "Welcome step ${index + 1} of 3" },
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (index == step) 8.dp else 6.dp)
                            .background(if (index == step) colors.accent else colors.divider, CircleShape))
                    }
                }
            }
            PrimaryButton(if (step == 2) "Make yourself at home" else "Next", onClick = {
                if (step == 2) onFinish() else onStep(step + 1)
            })
            OutlinedPillButton("Skip to sign in", onClick = onFinish)
        }
    }
}
