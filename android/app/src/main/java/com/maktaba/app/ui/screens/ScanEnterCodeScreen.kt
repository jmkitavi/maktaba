package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.maktaba.app.R
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.GreenButton
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Lending codes are uppercase alphanumeric, so the field shows them that way as you type. */
private object UppercaseTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString(text.text.uppercase()), androidx.compose.ui.text.input.OffsetMapping.Identity)
}

@Composable
fun ScanEnterCodeScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    var code by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember {
        GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build()
        )
    }

    fun submit() {
        if (loading || code.isBlank()) return
        loading = true
        errorText = null
        scope.launch {
            runCatching { LibraryRepository.resolveLoanCode(code) }
                .onSuccess {
                    navController.navigate(
                        Routes.ConfirmBorrow.createRoute(code.trim().uppercase())
                    )
                }
                .onFailure {
                    errorText = it.localizedMessage
                        ?: "That code is not valid, or it has already expired."
                }
            loading = false
        }
    }

    MaktabaScaffold(
        topBar = {
            ScreenTopBar(title = "Borrow a book", onBack = { navController.popBackStack() })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                // The screen previously had no scroll container, so on a short device the
                // keyboard pushed the primary action off the bottom of the screen.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(spacing.xs))
            Image(
                painter = painterResource(R.drawable.illus_enter_code),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(80.dp)
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                "Scan the lender's QR code, or type the code they sent you.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.inkSoft,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(spacing.md))

            // This is a button, not a viewfinder. It used to be drawn as a dark rectangle
            // with corner brackets, which reads as a live camera preview - so people held
            // the phone up to it and waited for a focus that never came.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaktabaShapes.medium)
                    .background(colors.surface)
                    .clickable(role = Role.Button) {
                        scope.launch {
                            runCatching { scanner.startScan().await().rawValue.orEmpty() }
                                .onSuccess { raw ->
                                    code = raw.substringAfter("code=", raw).trim().uppercase()
                                    errorText = null
                                }
                                .onFailure {
                                    errorText = "Scanning was cancelled, or the camera is unavailable."
                                }
                        }
                    }
                    .padding(spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PhotoCamera,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Scan QR code",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink
                    )
                    Text(
                        "Opens the camera scanner",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted
                    )
                }
            }

            Spacer(Modifier.height(spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
                Text(
                    "or",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = spacing.sm)
                )
                Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
            }
            Spacer(Modifier.height(spacing.md))

            Text(
                "Enter the code",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            Spacer(Modifier.height(spacing.xs))

            TextField(
                value = code,
                onValueChange = { code = it.uppercase(); errorText = null },
                modifier = Modifier.fillMaxWidth().clip(MaktabaShapes.small),
                placeholder = { Text("ABCD-1234", color = colors.inkMuted) },
                leadingIcon = {
                    Icon(Icons.Filled.Keyboard, contentDescription = null, tint = colors.inkMuted)
                },
                trailingIcon = {
                    if (code.isNotEmpty()) {
                        IconButton(onClick = { code = "" }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear code",
                                tint = colors.inkMuted
                            )
                        }
                    }
                },
                singleLine = true,
                isError = errorText != null,
                visualTransformation = UppercaseTransformation,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = { submit() }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedIndicatorColor = colors.primary,
                    unfocusedIndicatorColor = colors.divider,
                    errorIndicatorColor = colors.danger,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                    cursorColor = colors.primary
                )
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                errorText ?: "Codes look like ABCD-1234 and are shown on the lender's screen.",
                style = MaterialTheme.typography.bodySmall,
                color = if (errorText != null) colors.danger else colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(spacing.md))
            GreenButton(
                text = "Find this book",
                enabled = !loading && code.isNotBlank(),
                loading = loading,
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = ::submit
            )
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Preview(showBackground = true, name = "Borrow a book")
@Composable
private fun ScanEnterCodeScreenPreview() {
    BookHavenTheme { ScanEnterCodeScreen(navController = rememberNavController()) }
}
