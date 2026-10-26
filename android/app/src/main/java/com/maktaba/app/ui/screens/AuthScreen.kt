package com.maktaba.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseException
import com.maktaba.app.R
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.ui.components.*
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch
import java.io.IOException

internal enum class AuthPage(val title: String) {
    SIGN_IN("Sign in"), REGISTER("Create account"), RESET("Password reset"), RESET_SENT("Password reset")
}

internal data class AuthUiState(
    val page: AuthPage = AuthPage.SIGN_IN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val fieldErrors: Map<String, String> = emptyMap()
)

internal fun authValidation(state: AuthUiState): Map<String, String> = buildMap {
    if (state.page == AuthPage.REGISTER && (state.name.isBlank() || state.name.trim().length > 80)) {
        put("Name", "Enter a name of 1–80 characters.")
    }
    if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(state.email.trim())) {
        put("Email", "Enter a valid email address.")
    }
    if (state.page == AuthPage.SIGN_IN && state.password.isBlank()) put("Password", "Enter your password.")
    if (state.page == AuthPage.REGISTER && state.password.length < 6) put("Password", "Use at least 6 characters.")
}

@Composable
fun AuthScreen(onBack: () -> Unit = {}) {
    var state by remember { mutableStateOf(AuthUiState()) }
    val scope = rememberCoroutineScope()
    fun changePage(page: AuthPage) {
        if (!state.loading) state = state.copy(page = page, password = "", error = null, fieldErrors = emptyMap())
    }
    fun back() {
        if (state.page == AuthPage.SIGN_IN) onBack() else changePage(AuthPage.SIGN_IN)
    }
    BackHandler(state.loading || state.page != AuthPage.SIGN_IN) { if (!state.loading) back() }
    AuthContent(
        state, ::back,
        onField = { field, value ->
            state = when (field) {
                "Name" -> state.copy(name = value)
                "Email" -> state.copy(email = value)
                else -> state.copy(password = value)
            }.copy(fieldErrors = state.fieldErrors - field, error = null)
        },
        onPage = ::changePage,
        onVisibility = { state = state.copy(passwordVisible = !state.passwordVisible) },
        onSubmit = {
            if (!state.loading) {
                val errors = authValidation(state)
                state = state.copy(fieldErrors = errors, error = null)
                if (errors.isEmpty()) {
                    val request = state
                    state = state.copy(loading = true)
                    scope.launch {
                        try {
                            when (request.page) {
                                AuthPage.SIGN_IN -> FirebaseSession.signIn(request.email.trim(), request.password)
                                AuthPage.REGISTER -> FirebaseSession.register(request.name.trim(), request.email.trim(), request.password)
                                AuthPage.RESET -> {
                                    FirebaseSession.sendPasswordReset(request.email.trim())
                                    state = state.copy(page = AuthPage.RESET_SENT, password = "")
                                }
                                AuthPage.RESET_SENT -> Unit
                            }
                        } catch (error: FirebaseException) {
                            state = state.copy(error = error.localizedMessage ?: "We couldn't complete that request. Please try again.")
                        } catch (error: IOException) {
                            state = state.copy(error = "Check your connection and try again. Your details are still here.")
                        } finally {
                            state = state.copy(loading = false)
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun AuthContent(
    state: AuthUiState,
    onBack: () -> Unit,
    onField: (String, String) -> Unit,
    onPage: (AuthPage) -> Unit,
    onVisibility: () -> Unit,
    onSubmit: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val reset = state.page == AuthPage.RESET
    val registering = state.page == AuthPage.REGISTER
    SoulAccountPage(state.page.title, onBack = { if (!state.loading) onBack() },
        headerArt = if (state.page == AuthPage.SIGN_IN || registering) R.drawable.illus_cup_books else R.drawable.illus_book_sprig) {
        if (state.page == AuthPage.RESET_SENT) {
            SoulFeedbackHeader(
                R.drawable.illus_book_sprig, "Check your inbox.",
                "If an account uses ${state.email.trim()}, you'll receive instructions to reset its password."
            )
            SoulNotice("Reset instructions requested. Your password has not changed.")
            PrimaryButton("Back to sign in", onClick = { onPage(AuthPage.SIGN_IN) })
        } else {
            Text(
                if (reset) "A fresh start." else if (registering) "A little room for your stories." else "Good to have you back.",
                style = MaterialTheme.typography.headlineMedium, color = colors.ink,
                modifier = Modifier.padding(top = 13.dp)
            )
            Text(
                if (reset) "Enter your account email and we'll send reset instructions."
                else if (registering) "A place for your books, and the stories you share."
                else "Sign in to your account to reach your shelf and keep your loans close.",
                color = colors.inkMuted, style = MaterialTheme.typography.bodySmall
            )
            if (registering) SoulTextField("Name", state.name, { onField("Name", it) },
                error = state.fieldErrors["Name"], enabled = !state.loading)
            SoulTextField("Email", state.email, { onField("Email", it) }, state.fieldErrors["Email"],
                KeyboardType.Email, enabled = !state.loading)
            if (!reset) {
                SoulTextField("Password", state.password, { onField("Password", it) }, state.fieldErrors["Password"],
                    KeyboardType.Password, enabled = !state.loading,
                    visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    onDone = onSubmit)
                TextButton(onClick = onVisibility, enabled = !state.loading) {
                    Text(if (state.passwordVisible) "Hide password" else "Show password")
                }
            }
            state.error?.let { SoulNotice(it, error = true) }
            PrimaryButton(if (reset) "Send reset instructions" else if (registering) "Create account" else "Sign in",
                loading = state.loading, onClick = onSubmit)
            OutlinedPillButton(
                if (reset || registering) "Already have an account? Sign in" else "New here? Create an account",
                enabled = !state.loading,
                onClick = { onPage(if (registering || reset) AuthPage.SIGN_IN else AuthPage.REGISTER) }
            )
            if (!reset && !registering) OutlinedPillButton("Forgot password?", enabled = !state.loading,
                onClick = { onPage(AuthPage.RESET) })
        }
    }
}

@Composable
internal fun SoulFeedbackHeader(illustration: Int, title: String, body: String) {
    val colors = MaktabaTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(illustration), null,
            modifier = Modifier.padding(vertical = 20.dp).size(150.dp, 100.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.ink,
            textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
        Text(body, color = colors.inkMuted, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SoulAccountPage(
    title: String,
    onBack: (() -> Unit)? = null,
    branded: Boolean = false,
    headerArt: Int? = null,
    selectedTab: BottomNavTab? = null,
    onTabSelected: (BottomNavTab) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    MaktabaScaffold(selectedTab = selectedTab, onTabSelected = onTabSelected, topBar = {
        ScreenTopBar(title, onBack = onBack, branded = branded, compact = branded, actions = {
            headerArt?.let { Image(painterResource(it), null, modifier = Modifier.size(42.dp, 44.dp)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
internal fun SoulNotice(text: String, error: Boolean = false) {
    val colors = MaktabaTheme.colors
    Text(text, color = if (error) colors.danger else colors.inkMuted,
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
        modifier = Modifier.fillMaxWidth().background(if (error) colors.terracotta else colors.sage, MaktabaShapes.small)
            .padding(12.dp))
}

@Composable
internal fun SoulTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onDone: (() -> Unit)? = null,
    multiline: Boolean = false
) {
    val colors = MaktabaTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = colors.ink, style = MaterialTheme.typography.bodySmall)
        BasicTextField(
            value, onChange, modifier = Modifier.fillMaxWidth().heightIn(min = if (multiline) 130.dp else 48.dp)
                .background(colors.surface, MaktabaShapes.small)
                .border(if (focused) 2.dp else 1.dp,
                    if (error != null) colors.danger else if (focused) colors.primary else colors.divider, MaktabaShapes.small)
                .semantics { contentDescription = label }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.ink, lineHeight = 20.sp),
            singleLine = !multiline, minLines = if (multiline) 4 else 1,
            enabled = enabled, cursorBrush = SolidColor(colors.primary), interactionSource = interactions,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType,
                imeAction = if (onDone != null) ImeAction.Done else if (multiline) ImeAction.Default else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
        )
        error?.let { Text(it, color = colors.danger, style = MaterialTheme.typography.labelSmall) }
    }
}
