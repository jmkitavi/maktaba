package com.maktaba.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import com.maktaba.app.R
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.ui.components.OutlinedPillButton
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.theme.MaktabaTheme

@Composable
fun LogoutScreen(navController: NavHostController) {
    var signingOut by remember { mutableStateOf(false) }
    BackHandler(signingOut) {}
    LogoutContent(signingOut, onBack = { navController.popBackStack() }, onLogout = {
        if (!signingOut) {
            signingOut = true
            FirebaseSession.signOut()
        }
    })
}

@Composable
internal fun LogoutContent(signingOut: Boolean, onBack: () -> Unit, onLogout: () -> Unit) {
    val colors = MaktabaTheme.colors
    SoulAccountPage("Log out", onBack = { if (!signingOut) onBack() }, headerArt = R.drawable.illus_loan_shelf) {
        Text("Leaving your shelf for now?", color = colors.ink, style = MaterialTheme.typography.headlineMedium)
        Text("Your books will be here when you return. Sign in again to reach your library.",
            color = colors.inkMuted, style = MaterialTheme.typography.bodySmall)
        SoulNotice("Logging out on this device does not remove your books or close your loans.")
        PrimaryButton("Log out", loading = signingOut, onClick = onLogout)
        OutlinedPillButton("Stay with my shelf", enabled = !signingOut, onClick = onBack)
    }
}
