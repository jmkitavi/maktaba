package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.theme.MaktabaAppTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch

/** A banner distinguishes "we sent your reset email" from "that password was wrong". */
private sealed interface AuthNotice {
    data class Error(val text: String) : AuthNotice
    data class Success(val text: String) : AuthNotice
}

private const val MinPasswordLength = 6

@Composable
fun AuthScreen() {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing

    var registering by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<AuthNotice?>(null) }

    // Per-field errors, so the message sits under the field that caused it.
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun validate(): Boolean {
        nameError = if (registering && displayName.isBlank()) "Enter your name." else null
        emailError = when {
            email.isBlank() -> "Enter your email address."
            !email.contains("@") || !email.contains(".") -> "That does not look like an email address."
            else -> null
        }
        passwordError = when {
            password.isBlank() -> "Enter your password."
            registering && password.length < MinPasswordLength ->
                "Use at least $MinPasswordLength characters."
            else -> null
        }
        return nameError == null && emailError == null && passwordError == null
    }

    fun submit() {
        if (loading) return
        notice = null
        if (!validate()) return
        loading = true
        scope.launch {
            runCatching {
                if (registering) {
                    FirebaseSession.register(displayName.trim(), email.trim(), password)
                } else {
                    FirebaseSession.signIn(email.trim(), password)
                }
            }.onFailure {
                notice = AuthNotice.Error(it.localizedMessage ?: "We could not sign you in.")
            }
            loading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (registering) "Create your account" else "Welcome back",
                color = colors.ink,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                if (registering) "Start lending the books you already own."
                else "Sign in to reach your shelf and your loans.",
                color = colors.inkMuted,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(spacing.lg))

            if (registering) {
                AuthField(
                    value = displayName,
                    onValueChange = { displayName = it; nameError = null },
                    label = "Name",
                    error = nameError,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(spacing.sm))
            }

            AuthField(
                value = email,
                onValueChange = { email = it; emailError = null },
                label = "Email",
                error = emailError,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(spacing.sm))

            AuthField(
                value = password,
                onValueChange = { password = it; passwordError = null },
                label = "Password",
                error = passwordError,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onImeAction = ::submit,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password"
                            else "Show password",
                            tint = colors.inkMuted
                        )
                    }
                }
            )

            notice?.let { current ->
                Spacer(Modifier.height(spacing.sm))
                NoticeBanner(current)
            }

            Spacer(Modifier.height(spacing.md))
            PrimaryButton(
                text = if (registering) "Create account" else "Sign in",
                loading = loading,
                enabled = !loading,
                onClick = ::submit
            )

            if (!registering) {
                TextButton(
                    onClick = {
                        if (email.isBlank()) {
                            emailError = "Enter your email address first."
                            return@TextButton
                        }
                        emailError = null
                        scope.launch {
                            runCatching { FirebaseSession.sendPasswordReset(email.trim()) }
                                .onSuccess {
                                    notice = AuthNotice.Success(
                                        "Password reset email sent to ${email.trim()}."
                                    )
                                }
                                .onFailure {
                                    notice = AuthNotice.Error(
                                        it.localizedMessage ?: "We could not send that email."
                                    )
                                }
                        }
                    }
                ) {
                    Text(
                        "Forgot password?",
                        color = colors.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            TextButton(
                onClick = {
                    registering = !registering
                    notice = null
                    nameError = null
                    emailError = null
                    passwordError = null
                }
            ) {
                Text(
                    if (registering) "Already have an account? Sign in"
                    else "New here? Create an account",
                    color = colors.ink,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(spacing.sm))
            Text(
                "By continuing you agree to Maktaba's terms and privacy policy.",
                color = colors.inkMuted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MaktabaTheme.colors
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            visualTransformation = visualTransformation,
            trailingIcon = trailing,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() }
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = MaktabaShapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.divider,
                errorBorderColor = colors.danger,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                cursorColor = colors.primary
            )
        )
        if (error != null) {
            Text(
                error,
                color = colors.danger,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun NoticeBanner(notice: AuthNotice) {
    val colors = MaktabaTheme.colors
    val isError = notice is AuthNotice.Error
    val text = when (notice) {
        is AuthNotice.Error -> notice.text
        is AuthNotice.Success -> notice.text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isError) colors.dangerContainer else colors.successContainer,
                MaktabaShapes.small
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (isError) colors.danger else colors.success,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = if (isError) colors.danger else colors.success,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true, name = "Auth")
@Composable
private fun AuthScreenPreview() {
    MaktabaAppTheme { AuthScreen() }
}
