package com.maktaba.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.data.Book
import com.maktaba.app.R
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.*
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

data class LendBookConfigState(
    val book: Book,
    val borrowerName: String,
    val dueAt: Instant,
    val submitting: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendBookConfigScreen(navController: NavHostController, bookId: String) {
    val scope = rememberCoroutineScope()
    val clock = remember { Clock.systemDefaultZone() }
    var borrowerName by rememberSaveable(bookId) { mutableStateOf("") }
    var submitting by remember(bookId) { mutableStateOf(false) }
    var error by remember(bookId) { mutableStateOf<String?>(null) }
    var showPicker by rememberSaveable(bookId) { mutableStateOf(false) }
    var selectedDate by rememberSaveable(bookId) { mutableStateOf(defaultReturnPickerMillis(clock)) }
    val picker = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
    val book = LibraryRepository.books.find { it.id == bookId }
    val loadError = LibraryRepository.libraryError ?: LibraryRepository.loansError
    if (loadError != null) {
        UnavailableState("Could not load this book", loadError, navController::popBackStack,
            { navController.navigate(Routes.HomeLibrary.route) }, "Try again", LibraryRepository::retryLoads)
        return
    }
    if (!LibraryRepository.hasLoadedLibrary || !LibraryRepository.hasLoadedLoans) {
        LoadingState("Loading this book")
        return
    }
    if (book == null || !canLendCopy(book, LibraryRepository.activeLoanFor(bookId))) {
        UnavailableState(
            "Book unavailable",
            "Only a physical copy you own, with no open loan, can be lent. Unknown and digital editions cannot be lent.",
            navController::popBackStack, { navController.navigate(Routes.HomeLibrary.route) }
        )
        return
    }
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { selectedDate = it }
                    showPicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = picker) }
    }
    val dueAt = Instant.ofEpochMilli(LoanTimeFormatter.localDateToEndOfDayMillis(selectedDate, clock.zone))
    LendBookConfigContent(
        LendBookConfigState(book, borrowerName, dueAt, submitting, error),
        onBack = { navController.popBackStack() },
        onBorrowerName = { borrowerName = it; error = null },
        onDate = { picker.selectedDateMillis = selectedDate; showPicker = true },
        onGenerate = {
            if (!submitting) {
                when {
                    !dueAt.isAfter(clock.instant()) -> error = "Pick a return date in the future."
                    LibraryRepository.books.find { it.id == bookId }?.let {
                        canLendCopy(it, LibraryRepository.activeLoanFor(bookId))
                    } != true -> error = "This copy is no longer available to lend."
                    else -> {
                        submitting = true
                        error = null
                        scope.launch(Dispatchers.Main.immediate) {
                            handoffAttempt { LibraryRepository.startLending(bookId, borrowerName.trim(), dueAt.toEpochMilli()) }
                                .onSuccess {
                                    navController.currentBackStackEntry?.savedStateHandle
                                        ?.set(inviteBorrowerLabelKey(it.id), borrowerName.trim())
                                    navController.navigate(Routes.ShareLendingCode.createRoute(it.id))
                                }
                                .onFailure { error = it.localizedMessage ?: "Could not create the invitation. Try again." }
                            submitting = false
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun LendBookConfigContent(
    state: LendBookConfigState,
    onBack: () -> Unit,
    onBorrowerName: (String) -> Unit,
    onDate: () -> Unit,
    onGenerate: () -> Unit
) {
    val colors = MaktabaTheme.colors
    HandoffPage("Lend a Book", onBack, illustrated = true, actions = {
        PrimaryButton("Generate Lending Code", enabled = !state.submitting,
            loading = state.submitting, leadingIcon = {
                Icon(Icons.Filled.QrCodeScanner, null, modifier = Modifier.size(16.dp))
            }, onClick = onGenerate)
    }) {
        HandoffBookSummary(state.book)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarToday, null, modifier = Modifier.size(14.dp), tint = colors.primary)
            Spacer(Modifier.width(7.dp))
            Text("Return By", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = colors.ink)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onDate, enabled = !state.submitting, shape = MaktabaShapes.small,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            contentPadding = PaddingValues(horizontal = 13.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.ink)) {
            Text(handoffDateText(state.dueAt, numeric = true), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall)
            Icon(Icons.Outlined.CalendarToday, null, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("In the app, both readers get a reminder before this date.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 18.sp),
            color = colors.inkMuted)
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Person, null, modifier = Modifier.size(14.dp), tint = colors.primary)
            Spacer(Modifier.width(7.dp))
            Text("Who Is Borrowing?", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, letterSpacing = 0.3.sp),
                color = colors.ink)
            Text("Optional", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = colors.inkMuted)
        }
        Spacer(Modifier.height(10.dp))
        HandoffTextField(
            state.borrowerName, onBorrowerName, singleLine = true, enabled = !state.submitting,
            placeholder = "Borrower's name",
            modifier = Modifier.semantics { contentDescription = "Borrower's name" }
        )
        Spacer(Modifier.height(10.dp))
        Text("A label for your own reference, not an account invitation.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 18.sp),
            color = colors.inkMuted)
        Spacer(Modifier.height(30.dp))
        HandoffNotice("The loan starts only when the borrower has the book and confirms the handover.",
            icon = Icons.AutoMirrored.Outlined.MenuBook)
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            HandoffNotice(it, error = true)
        }
        Spacer(Modifier.height(14.dp))
        Image(
            painterResource(R.drawable.illus_reading_lamp), null, contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.End).width(138.dp).aspectRatio(350f / 230f)
                .graphicsLayer { alpha = if (colors.isDark) 0.75f else 0.85f }
        )
    }
}
