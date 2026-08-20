package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.theme.CreamBackground
import com.maktaba.app.ui.theme.InkBrown
import com.maktaba.app.ui.theme.MutedText
import com.maktaba.app.ui.theme.SerifDisplay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    var registering by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(CreamBackground).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (registering) "Create your account" else "Welcome back",
                color = InkBrown,
                fontFamily = SerifDisplay,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (registering) "Start building and sharing your library." else "Sign in to access your library.",
                color = MutedText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            if (registering) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(message!!, color = InkBrown, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = when {
                    registering -> "Create Account"
                    else -> "Sign In"
                },
                loading = loading,
                enabled = !loading,
                onClick = {
                    if (loading) return@PrimaryButton
                    if (email.isBlank() || password.length < 6 || (registering && displayName.isBlank())) {
                        message = "Enter a valid email, name, and a password of at least 6 characters."
                        return@PrimaryButton
                    }
                    loading = true
                    message = null
                    scope.launch {
                        runCatching {
                            if (registering) {
                                FirebaseSession.register(displayName, email, password)
                            } else {
                                FirebaseSession.signIn(email, password)
                            }
                        }.onFailure { message = it.localizedMessage ?: "Authentication failed." }
                        loading = false
                    }
                }
            )
            if (!registering) {
                TextButton(
                    onClick = {
                        if (email.isBlank()) {
                            message = "Enter your email address first."
                        } else {
                            scope.launch {
                                runCatching { FirebaseSession.sendPasswordReset(email) }
                                    .onSuccess { message = "Password reset email sent." }
                                    .onFailure { message = it.localizedMessage ?: "Could not send reset email." }
                            }
                        }
                    }
                ) {
                    Text("Forgot password?", color = InkBrown)
                }
            }
            Text(
                if (registering) "Already have an account? Sign in" else "New here? Create an account",
                color = InkBrown,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    registering = !registering
                    message = null
                }.padding(12.dp)
            )
        }
    }
}
