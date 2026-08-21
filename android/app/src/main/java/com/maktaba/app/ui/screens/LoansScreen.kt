package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.EmptyState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.StatusPill
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.components.statusVisuals
import com.maktaba.app.ui.components.urgencyColor
import com.maktaba.app.ui.theme.MaktabaAppTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency

private enum class LoanTab(val label: String) { LENT_OUT("Lent out"), BORROWED("Borrowed") }

/**
 * A top-level home for loans. Reaching an active loan previously meant Library, then the
 * "Lent Out" filter, then the book, then its detail screen, then "View Active Loan" -
 * four taps to find out something was overdue.
 */
@Composable
fun LoansScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    var tab by remember { mutableStateOf(LoanTab.LENT_OUT) }

    val loans = LibraryRepository.activeLoans
    val visible = loans
        .filter { if (tab == LoanTab.LENT_OUT) it.isLender else !it.isLender }
        // Overdue first, then soonest due. The urgent items must never be buried.
        .sortedWith(
            compareBy(
                { LoanTimeFormatter.daysUntilDue(it.dueAt) ?: Long.MAX_VALUE },
                { it.counterpartyName.lowercase() }
            )
        )

    MaktabaScaffold(
        selectedTab = BottomNavTab.LOANS,
        onTabSelected = { navController.navigateToTab(it) },
        topBar = { ScreenTopBar(title = "Loans") }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = colors.background,
                contentColor = colors.primary
            ) {
                LoanTab.values().forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        selectedContentColor = colors.primary,
                        unselectedContentColor = colors.inkMuted,
                        text = {
                            val count = loans.count {
                                if (entry == LoanTab.LENT_OUT) it.isLender else !it.isLender
                            }
                            Text(
                                if (count > 0) "${entry.label} ($count)" else entry.label,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            }

            if (visible.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    title = if (tab == LoanTab.LENT_OUT) "Nothing lent out" else "Nothing borrowed",
                    message = if (tab == LoanTab.LENT_OUT) {
                        "When you lend a book, it will appear here with its return date."
                    } else {
                        "Books you claim with a lending code will appear here."
                    },
                    primaryLabel = if (tab == LoanTab.LENT_OUT) null else "Enter a lending code",
                    onPrimary = if (tab == LoanTab.LENT_OUT) {
                        null
                    } else {
                        { navController.navigate(Routes.ScanEnterCode.route) }
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    items(visible, key = { it.id }) { loan ->
                        val book = LibraryRepository.bookById(loan.bookId)
                        if (book != null) {
                            LoanRow(
                                book = book,
                                loan = loan,
                                onClick = {
                                    navController.navigate(
                                        Routes.ActiveLoan.createRoute(loan.bookId)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanRow(book: Book, loan: ActiveLoan, onClick: () -> Unit) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val urgency = LoanTimeFormatter.urgency(loan.dueAt)
    val visuals = statusVisuals(colors, book.status, urgency)
    val dueLine = LoanTimeFormatter.shortRemaining(loan.dueAt)
    val relation = if (loan.isLender) "with ${loan.counterpartyName}" else "from ${loan.counterpartyName}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaktabaShapes.medium)
            .background(colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = "${book.title}, $relation. $dueLine. ${visuals.label}"
            },
        verticalAlignment = Alignment.Top
    ) {
        BookCoverImage(
            book = book,
            contentScale = ContentScale.Crop,
            decorative = true,
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(0.68f)
                .clip(MaktabaShapes.cover)
        )
        Spacer(Modifier.width(spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                relation,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted
            )
            Spacer(Modifier.height(spacing.xxs))
            Text(
                dueLine,
                style = MaterialTheme.typography.labelMedium,
                color = urgencyColor(urgency)
            )
        }
        Spacer(Modifier.width(spacing.xs))
        if (urgency == LoanUrgency.OVERDUE || urgency == LoanUrgency.DUE_TODAY) {
            StatusPill(visuals = visuals, compact = true, decorative = true)
        }
    }
}

@Preview(showBackground = true, name = "Loans")
@Composable
private fun LoansScreenPreview() {
    MaktabaAppTheme { LoansScreen(navController = rememberNavController()) }
}
