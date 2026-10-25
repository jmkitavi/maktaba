package com.maktaba.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavHostController
import com.google.firebase.FirebaseException
import com.maktaba.app.R
import com.maktaba.app.data.LibraryReportReason
import com.maktaba.app.data.LibraryReports
import com.maktaba.app.data.LoadState
import com.maktaba.app.data.reportValidationError
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.OutlinedPillButton
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.TextActionButton
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch
import java.util.UUID

enum class ReportStep { FORM, REVIEW, SUCCESS }

data class ReportUiState(
    val libraryName: String,
    val reason: LibraryReportReason? = null,
    val details: String = "",
    val step: ReportStep = ReportStep.FORM,
    val sending: Boolean = false,
    val error: String? = null,
    val ownerDisplayName: String = ""
)

@Composable
fun ReportLibraryScreen(navController: NavHostController, ownerUid: String) {
    var target by remember(ownerUid) { mutableStateOf<LoadState<LibraryReports.Target>>(LoadState.Loading) }
    var reload by remember { mutableStateOf(0) }
    var reasonName by rememberSaveable(ownerUid) { mutableStateOf<String?>(null) }
    var details by rememberSaveable(ownerUid) { mutableStateOf("") }
    var step by rememberSaveable(ownerUid) { mutableStateOf(ReportStep.FORM) }
    val requestId = rememberSaveable(ownerUid) { UUID.randomUUID().toString() }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val reason = LibraryReportReason.values().find { it.name == reasonName }
    val scope = rememberCoroutineScope()
    val back: () -> Unit = {
        if (!sending) {
            if (step == ReportStep.REVIEW) step = ReportStep.FORM else navController.popBackStack()
        }
    }
    BackHandler { back() }
    LaunchedEffect(ownerUid, reload) {
        target = LoadState.Loading
        target = try {
            LoadState.Ready(LibraryReports.target(ownerUid))
        } catch (failure: FirebaseException) {
            LoadState.Error(failure)
        } catch (failure: IllegalStateException) {
            LoadState.Error(failure)
        } catch (failure: IllegalArgumentException) {
            LoadState.Error(failure)
        }
    }
    when (val loaded = target) {
        is LoadState.Ready -> ReportLibraryContent(
            ReportUiState(loaded.value.libraryName, reason, details, step, sending, error, loaded.value.ownerDisplayName),
            onBack = back,
            onTabSelected = navController::navigateToTab,
            onReason = { reasonName = it.name; error = null },
            onDetails = { details = it; error = null },
            onReview = {
                error = reportValidationError(reason, details)
                if (error == null) step = ReportStep.REVIEW
            },
            onSubmit = {
                val selectedReason = reason
                val validation = reportValidationError(selectedReason, details)
                if (validation != null) {
                    error = validation
                } else if (!sending && selectedReason != null) {
                    sending = true
                    error = null
                    scope.launch {
                        try {
                            LibraryReports.submit(ownerUid, selectedReason, details, requestId)
                            step = ReportStep.SUCCESS
                        } catch (failure: FirebaseException) {
                            error = failure.localizedMessage ?: "The report could not be submitted. Please retry."
                        } catch (failure: IllegalStateException) {
                            error = failure.message ?: "The report could not be submitted. Please retry."
                        } catch (failure: IllegalArgumentException) {
                            error = failure.message ?: "Check the report details and try again."
                        } finally {
                            sending = false
                        }
                    }
                }
            }
        )
        else -> MaktabaScaffold(
            topBar = { ScreenTopBar("Report library", onBack = back) },
            selectedTab = BottomNavTab.COLLECTIONS, onTabSelected = navController::navigateToTab
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (loaded) {
                    LoadState.Loading -> LoadingState()
                    is LoadState.Error -> {
                        Text(loaded.cause.localizedMessage ?: "This library could not be loaded.", color = MaktabaTheme.colors.danger)
                        PrimaryButton("Try again", onClick = { reload++ })
                        TextActionButton("Go back", onClick = back)
                    }
                    LoadState.NotFound -> {
                        Text("This library is no longer shared.", color = MaktabaTheme.colors.inkMuted)
                        TextActionButton("Go back", onClick = back)
                    }
                    is LoadState.Ready -> Unit
                }
            }
        }
    }
}

@Composable
internal fun ReportLibraryContent(
    state: ReportUiState,
    onBack: () -> Unit,
    onReason: (LibraryReportReason) -> Unit,
    onDetails: (String) -> Unit,
    onReview: () -> Unit,
    onSubmit: () -> Unit,
    onTabSelected: (BottomNavTab) -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    val bodyStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
    MaktabaScaffold(topBar = {
        ScreenTopBar(when (state.step) {
            ReportStep.FORM -> "Report library"
            ReportStep.REVIEW -> "Review report"
            ReportStep.SUCCESS -> "Report outcome"
        }, onBack = onBack)
    }, selectedTab = BottomNavTab.COLLECTIONS, onTabSelected = onTabSelected) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.step) {
                ReportStep.FORM -> {
                    Text(
                        "Reporting ${state.libraryName}" +
                            state.ownerDisplayName.takeIf(String::isNotBlank)?.let { ", shared by $it." }.orEmpty(),
                        style = bodyStyle, color = colors.inkMuted
                    )
                    Text("Reason · Required", style = MaterialTheme.typography.bodySmall, color = colors.ink)
                    Column(Modifier.selectableGroup()) {
                        LibraryReportReason.values().forEach { reason ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .background(if (state.reason == reason) colors.sage else androidx.compose.ui.graphics.Color.Transparent, MaktabaShapes.small)
                                    .selectable(
                                        selected = state.reason == reason, role = Role.RadioButton,
                                        onClick = { onReason(reason) }
                                    ).padding(horizontal = 5.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = state.reason == reason, onClick = null, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(13.dp))
                                Text(reason.label, style = MaterialTheme.typography.bodySmall, color = colors.ink)
                            }
                            HorizontalDivider(color = colors.divider)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Details (optional)", style = MaterialTheme.typography.bodySmall, color = colors.ink)
                        OutlinedTextField(
                            value = state.details, onValueChange = onDetails,
                            placeholder = { Text("What should the app operator know?", style = bodyStyle) },
                            textStyle = bodyStyle,
                            isError = state.details.length > 500,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp),
                            minLines = 5, maxLines = 7, shape = RoundedCornerShape(9.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface,
                                unfocusedBorderColor = colors.divider, focusedBorderColor = colors.primary
                            )
                        )
                        Text("${state.details.length}/500", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colors.inkMuted, modifier = Modifier.align(Alignment.End))
                    }
                    Text(
                        "Please avoid contact details or sensitive personal information. A report does not automatically remove the library.",
                        style = bodyStyle, color = colors.inkMuted
                    )
                    PrimaryButton("Review report", enabled = reportValidationError(state.reason, state.details) == null, onClick = onReview)
                    ReportError(state.error)
                }
                ReportStep.REVIEW -> {
                    Text("Check before submitting.", style = MaterialTheme.typography.headlineMedium, color = colors.ink, modifier = Modifier.padding(top = 13.dp))
                    Column(
                        Modifier.fillMaxWidth().background(colors.sage, MaktabaShapes.medium)
                            .border(1.dp, colors.divider, MaktabaShapes.medium).padding(16.dp)
                    ) {
                        ReportReviewField("Library", state.libraryName +
                            state.ownerDisplayName.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty())
                        ReportReviewField("Reason", state.reason?.label ?: "Choose a reason first")
                        ReportReviewField("Details", state.details.ifBlank { "No additional details" })
                    }
                    Text(
                        "The app operator receives this report for manual review. No automatic takedown or resolution time is promised.",
                        style = bodyStyle, color = colors.inkMuted
                    )
                    ReportError(state.error)
                    PrimaryButton(
                        "Submit report", loading = state.sending,
                        enabled = reportValidationError(state.reason, state.details) == null, onClick = onSubmit
                    )
                    OutlinedPillButton("Edit report", enabled = !state.sending, onClick = onBack)
                }
                ReportStep.SUCCESS -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painterResource(R.drawable.illus_home_icon), contentDescription = null,
                            modifier = Modifier.padding(vertical = 20.dp).width(150.dp).height(100.dp)
                        )
                        Text("Report submitted.", style = MaterialTheme.typography.headlineMedium, color = colors.ink, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp))
                        Text("Your report was accepted for manual review.", style = bodyStyle, color = colors.inkMuted, textAlign = TextAlign.Center)
                    }
                    Text(
                        "No moderation outcome or review timeline is promised. This library remains visible unless its owner changes sharing or a review determines otherwise.",
                        style = bodyStyle, color = colors.accent,
                        modifier = Modifier.fillMaxWidth().background(colors.sage, MaktabaShapes.small)
                            .border(1.dp, colors.divider, MaktabaShapes.small).padding(12.dp)
                    )
                    PrimaryButton("Return to library", onClick = onBack)
                }
            }
        }
    }
}

@Composable
private fun ReportReviewField(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaktabaTheme.colors.inkMuted)
    Text(
        value, style = MaterialTheme.typography.bodyLarge, color = MaktabaTheme.colors.ink,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
    )
}

@Composable
private fun ReportError(message: String?) {
    message?.let { Text(it, color = MaktabaTheme.colors.danger, style = MaterialTheme.typography.bodySmall) }
}
