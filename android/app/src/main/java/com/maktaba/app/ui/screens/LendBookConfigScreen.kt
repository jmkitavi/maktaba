package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.BookFormat
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.MinTouchTarget
import com.maktaba.app.util.LoanTimeFormatter
import kotlinx.coroutines.launch

private const val DefaultLoanDays = 14L
private const val DayMillis = 24L * 60 * 60 * 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendBookConfigScreen(navController: NavHostController, bookId: String) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val scope = rememberCoroutineScope()

    var borrowerName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val book = LibraryRepository.bookById(bookId)
    if (book == null) {
        UnavailableState(
            title = "Book unavailable",
            message = "This book is no longer on your shelf, so it cannot be lent.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }
    if (book.format == BookFormat.DIGITAL) {
        UnavailableState(
            title = "Digital edition",
            message = buildString {
                append("Digital editions cannot be lent through Book Haven.")
                if (book.physicalEditionIsbn13.isNotBlank()) {
                    append(" The physical edition is ISBN ")
                    append(book.physicalEditionIsbn13)
                    append(".")
                }
            },
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }

    val defaultDueMillis = remember { System.currentTimeMillis() + DefaultLoanDays * DayMillis }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = defaultDueMillis)
    // Cancel used to leave the newly picked date in place, because both dialog buttons did
    // nothing but close the dialog. The value is snapshotted on open and restored on dismiss.
    var dateBeforeEditing by remember { mutableStateOf(defaultDueMillis) }

    val selectedDateMillis = datePickerState.selectedDateMillis ?: defaultDueMillis
    val dueAtMillis = LoanTimeFormatter.localDateToEndOfDayMillis(selectedDateMillis)
    val dueDateLabel = LoanTimeFormatter.formatDate(java.time.Instant.ofEpochMilli(dueAtMillis))
    val suggestions = LibraryRepository.recentBorrowerNames

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = {
                datePickerState.selectedDateMillis = dateBeforeEditing
                showDatePicker = false
            },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis = dateBeforeEditing
                    showDatePicker = false
                }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    MaktabaScaffold(
        topBar = { ScreenTopBar(title = "Lend book", onBack = { navController.popBackStack() }) }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = spacing.gutter)
        ) {
            Spacer(Modifier.height(spacing.sm))

            MaktabaCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    BookCoverImage(
                        book = book,
                        contentScale = ContentScale.Crop,
                        decorative = true,
                        modifier = Modifier
                            .width(88.dp)
                            .aspectRatio(0.68f)
                            .clip(MaktabaShapes.cover)
                    )
                    Spacer(Modifier.width(spacing.md))
                    Column {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(spacing.xxs))
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkMuted
                        )
                        if (book.genre.isNotBlank()) {
                            Spacer(Modifier.height(spacing.xs))
                            Text(
                                book.genre,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.inkSoft,
                                modifier = Modifier
                                    .clip(MaktabaShapes.pill)
                                    .background(colors.surfaceAlt)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.lg))

            FieldLabel(Icons.Filled.CalendarToday, "Return by")
            Spacer(Modifier.height(spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .clip(MaktabaShapes.small)
                    .background(colors.surface)
                    .clickable(role = Role.Button) {
                        dateBeforeEditing = datePickerState.selectedDateMillis ?: defaultDueMillis
                        showDatePicker = true
                    }
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.CalendarToday,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(spacing.xs))
                Text(
                    dueDateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.inkMuted
                )
            }
            Spacer(Modifier.height(spacing.xxs))
            Text(
                "Both of you will be reminded before this date.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted
            )

            Spacer(Modifier.height(spacing.lg))

            FieldLabel(Icons.Filled.Person, "Who is borrowing it?", optional = true)
            Spacer(Modifier.height(spacing.xs))

            if (suggestions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    items(suggestions) { name ->
                        AssistChip(
                            onClick = { borrowerName = name },
                            label = {
                                Text(name, style = MaterialTheme.typography.labelMedium)
                            },
                            shape = MaktabaShapes.pill,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = colors.chipUnselected,
                                labelColor = colors.onChipUnselected
                            ),
                            border = null
                        )
                    }
                }
                Spacer(Modifier.height(spacing.xs))
            }

            TextField(
                value = borrowerName,
                onValueChange = { borrowerName = it },
                modifier = Modifier.fillMaxWidth().clip(MaktabaShapes.small),
                placeholder = { Text("e.g. Alex Johnson", color = colors.inkMuted) },
                leadingIcon = {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = colors.inkMuted)
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedIndicatorColor = colors.primary,
                    unfocusedIndicatorColor = colors.divider,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink,
                    cursorColor = colors.primary
                )
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                "A name makes the loan easier to recognise later.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted
            )

            Spacer(Modifier.height(spacing.lg))
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                Spacer(Modifier.height(spacing.xs))
            }
            PrimaryButton(
                text = "Generate lending code",
                loading = loading,
                enabled = !loading,
                leadingIcon = {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    if (loading) return@PrimaryButton
                    if (dueAtMillis <= System.currentTimeMillis()) {
                        error = "Pick a return date in the future."
                        return@PrimaryButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        runCatching {
                            LibraryRepository.startLending(
                                bookId = book.id,
                                borrowerName = borrowerName.trim().ifBlank { "a friend" },
                                dueAtMillis = dueAtMillis
                            )
                        }.onSuccess { invite ->
                            navController.navigate(
                                Routes.ShareLendingCode.createRoute(invite.id)
                            )
                        }.onFailure {
                            error = it.localizedMessage
                                ?: "We could not create a lending invitation."
                        }
                        loading = false
                    }
                }
            )
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun FieldLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    optional: Boolean = false
) {
    val colors = MaktabaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { heading() }
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = colors.ink)
        if (optional) {
            Spacer(Modifier.width(6.dp))
            Text(
                "(optional)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted
            )
        }
    }
}

@Preview(showBackground = true, name = "Lend book")
@Composable
private fun LendBookConfigScreenPreview() {
    BookHavenTheme {
        LendBookConfigScreen(navController = rememberNavController(), bookId = "preview")
    }
}
