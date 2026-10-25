package com.maktaba.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.maktaba.app.R
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ScanEnterCodeState(
    val code: String = "",
    val resolving: Boolean = false,
    val scanning: Boolean = false,
    val error: String? = null
)

@Composable
fun ScanEnterCodeScreen(navController: NavHostController) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scanner = remember(context) {
        GmsBarcodeScanning.getClient(context, GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build())
    }
    fun submit() {
        if (resolving || scanning) return
        val normalized = parseLendingCode(code)
        if (normalized == null) {
            error = "Enter a code like ABCD-1234 or a valid Book Haven lending link."
            return
        }
        resolving = true
        error = null
        scope.launch(Dispatchers.Main.immediate) {
            handoffAttempt { LibraryRepository.resolveLoanCode(normalized) }
                .onSuccess { navController.navigate(Routes.ConfirmBorrow.createRoute(normalized)) }
                .onFailure { error = it.localizedMessage ?: "Could not find this invitation. Check the code and try again." }
            resolving = false
        }
    }
    ScanEnterCodeContent(
        ScanEnterCodeState(code, resolving, scanning, error),
        onBack = { navController.popBackStack() },
        onCode = { code = it; error = null },
        onFind = ::submit,
        onScan = {
            if (!scanning && !resolving) {
                scanning = true
                error = null
                scope.launch(Dispatchers.Main.immediate) {
                    try {
                        val raw = scanner.startScan().await().rawValue.orEmpty()
                        val parsed = parseLendingCode(raw)
                        if (parsed == null) error = "That QR is not a valid Book Haven lending code. Try again or type the code."
                        else code = parsed
                    } catch (cancelled: CancellationException) {
                        currentCoroutineContext().ensureActive()
                        error = "Scan cancelled. You can try again or enter the code below."
                    } catch (failure: Exception) {
                        error = "The camera scanner could not open. Try again or enter the code below."
                    } finally {
                        scanning = false
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanEnterCodeContent(
    state: ScanEnterCodeState,
    onBack: () -> Unit,
    onCode: (String) -> Unit,
    onScan: () -> Unit,
    onFind: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val busy = state.scanning || state.resolving
    HandoffPage("Borrow a Book", onBack, actions = {
        PrimaryButton("Find This Book", loading = state.resolving,
            enabled = !busy && state.code.isNotBlank(), leadingIcon = {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, modifier = Modifier.size(16.dp))
            }, onClick = onFind)
    }) {
        Spacer(Modifier.height(15.dp))
        Image(painterResource(R.drawable.illus_enter_code), null, contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.CenterHorizontally).width(164.dp).aspectRatio(1012f / 414f))
        Spacer(Modifier.height(26.dp))
        LoanIntro("A shared story awaits.", "Scan the lender's QR code, or enter the code they sent you.")
        Spacer(Modifier.height(22.dp))
        OutlinedCard(onClick = onScan, enabled = !busy, shape = MaktabaShapes.medium,
            border = BorderStroke(1.dp, colors.sageLine),
            colors = CardDefaults.outlinedCardColors(
                containerColor = colors.sage, disabledContainerColor = colors.sage
            )) {
            Row(Modifier.fillMaxWidth().heightIn(min = 74.dp).padding(horizontal = 13.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PhotoCamera, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (state.scanning) "Opening scanner…" else "Scan QR Code",
                        style = MaterialTheme.typography.labelLarge, color = colors.ink)
                    Spacer(Modifier.height(5.dp))
                    Text("Opens the camera scanner in the app",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = colors.inkMuted)
                }
                Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(16.dp), tint = colors.inkMuted)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = colors.divider)
            Text("or", modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            HorizontalDivider(Modifier.weight(1f), color = colors.divider)
        }
        Spacer(Modifier.height(24.dp))
        Text("Enter the Code", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = colors.ink)
        Spacer(Modifier.height(10.dp))
        HandoffTextField(
            value = state.code, onValueChange = onCode, enabled = !busy,
            containerColor = colors.ochre,
            placeholder = "ABCD-1234", singleLine = true, isError = state.error != null,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = 2.sp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .semantics { contentDescription = "Enter the Code" },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (!busy) onFind() })
        )
        Spacer(Modifier.height(10.dp))
        Text(state.error ?: "Codes look like ABCD-1234. You can also paste the lender's link.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 18.sp),
            color = if (state.error == null) colors.inkMuted else colors.danger)
    }
}
