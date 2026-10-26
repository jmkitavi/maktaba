package com.maktaba.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.R
import com.maktaba.app.data.ActiveLoan
import com.maktaba.app.data.Book
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.EmptyState
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.StatusPill
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.components.statusVisuals
import com.maktaba.app.ui.components.urgencyColor
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.nav.Routes
import com.maktaba.app.util.LoanTimeFormatter
import com.maktaba.app.util.LoanUrgency
import java.time.Clock

@Composable
fun LoansScreen(navController: NavHostController) {
    LoansContent(
        books = LibraryRepository.books,
        loans = LibraryRepository.activeLoans,
        loaded = LibraryRepository.hasLoadedLoans && LibraryRepository.hasLoadedLibrary,
        error = LibraryRepository.loansError ?: LibraryRepository.libraryError,
        onLoan = { navController.navigate(Routes.ActiveLoan.createRoute(it.bookId)) },
        onChooseBook = { navController.navigateToTab(BottomNavTab.LIBRARY) },
        onBorrow = { navController.navigate(Routes.ScanEnterCode.route) },
        onTab = { navController.navigateToTab(it) },
        onRetry = LibraryRepository::retryLoads
    )
}

@Composable
fun LoansContent(
    books: List<Book>,
    loans: List<ActiveLoan>,
    loaded: Boolean,
    error: String?,
    onLoan: (ActiveLoan) -> Unit,
    onChooseBook: () -> Unit,
    onBorrow: () -> Unit,
    onTab: (BottomNavTab) -> Unit,
    onRetry: () -> Unit,
    initiallyLender: Boolean = true,
    clock: Clock = Clock.systemDefaultZone()
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    var lender by rememberSaveable { mutableStateOf(initiallyLender) }
    val visible = visibleLibraryLoans(loans, lender)
    val phase = libraryFeedPhase(loaded, error, visible.size)

    MaktabaScaffold(
        selectedTab = BottomNavTab.LOANS,
        onTabSelected = onTab,
        topBar = {
            ScreenTopBar(
                title = "Loans",
                branded = true,
                illustrationRes = R.drawable.illus_home_icon
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = if (lender) 0 else 1,
                containerColor = Color.Transparent,
                contentColor = colors.primary,
                modifier = Modifier.padding(horizontal = spacing.gutter),
                divider = { HorizontalDivider(color = colors.divider) },
                indicator = { positions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(positions[if (lender) 0 else 1]),
                        height = 2.dp, color = colors.primary
                    )
                }
            ) {
                listOf(true, false).forEach { isLender ->
                    Tab(
                        modifier = Modifier.height(46.dp),
                        selected = lender == isLender,
                        onClick = { lender = isLender },
                        selectedContentColor = colors.primary,
                        unselectedContentColor = colors.inkSoft,
                        text = {
                            val label = if (isLender) "Lent Out" else "Borrowed"
                            val count = visibleLibraryLoans(loans, isLender).size
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal)
                                )
                                if (loaded && error == null && count > 0) {
                                    Text(
                                        count.toString(),
                                        modifier = Modifier.clip(CircleShape).background(colors.surfaceAlt)
                                            .padding(horizontal = 5.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp, lineHeight = 12.sp
                                        ),
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }
                    )
                }
            }
            when (phase) {
                LibraryFeedPhase.ERROR -> EmptyState(
                    modifier = Modifier.weight(1f),
                    title = "Your loans could not load",
                    message = error.orEmpty(),
                    primaryLabel = "Try again",
                    onPrimary = onRetry
                )
                LibraryFeedPhase.LOADING -> Box(Modifier.weight(1f)) {
                    LoadingState("Loading your loans")
                }
                LibraryFeedPhase.EMPTY, LibraryFeedPhase.NO_RESULTS -> EmptyState(
                    modifier = Modifier.weight(1f),
                    illustrationRes = R.drawable.illus_loan_shelf,
                    title = if (lender) "Nothing lent out" else "Nothing borrowed",
                    message = if (lender) {
                        "A good book deserves another reader. Choose a physical book from your shelf."
                    } else {
                        "A borrowed book is a shared story. Enter the code your lender sent you."
                    },
                    primaryLabel = if (lender) "Choose a Book to Lend" else "Borrow with a Code",
                    onPrimary = if (lender) onChooseBook else onBorrow
                )
                LibraryFeedPhase.CONTENT -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = spacing.gutter, end = spacing.gutter,
                        top = 13.dp, bottom = 25.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Schedule, contentDescription = null,
                                tint = colors.inkMuted, modifier = Modifier.size(12.dp)
                            )
                            Text(
                                "Closest return dates first",
                                color = colors.inkMuted,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp, lineHeight = 16.sp,
                                    lineHeightStyle = CoreLibraryLineHeight
                                )
                            )
                        }
                    }
                    itemsIndexed(visible, key = { _, loan -> loan.id }) { index, loan ->
                        LibraryLoanRow(
                            book = books.firstOrNull { it.id == loan.bookId },
                            loan = loan,
                            containerColor = if ((index % 2 == 0) == lender) colors.sage else colors.ochre,
                            clock = clock,
                            onClick = { onLoan(loan) }
                        )
                    }
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(top = 13.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (lender) "A good book deserves another reader."
                                else "A borrowed book is a shared story.",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 13.sp, lineHeight = 22.1.sp,
                                    lineHeightStyle = CoreLibraryLineHeight
                                ),
                                fontStyle = FontStyle.Italic,
                                color = colors.inkSoft,
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = if (lender) onChooseBook else onBorrow) {
                                Text(
                                    if (lender) "Choose a Book to Lend" else "Borrow with a Code",
                                    color = colors.terraAction,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null, tint = colors.terraAction,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryLoanRow(
    book: Book?,
    loan: ActiveLoan,
    containerColor: Color,
    clock: Clock,
    onClick: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val urgency = LoanTimeFormatter.urgency(loan.dueAt, clock)
    val status = if (loan.isLender) BookStatus.LENT_OUT else BookStatus.BORROWED

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = MaktabaShapes.medium,
        color = containerColor,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, colors.divider)
    ) {
        Box(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (book != null) {
                    BookCoverImage(
                        book = book, contentScale = ContentScale.Fit, decorative = true,
                        modifier = Modifier.width(58.dp).aspectRatio(2f / 3f)
                            .shadow(3.dp, MaktabaShapes.cover).clip(MaktabaShapes.cover)
                    )
                    Spacer(Modifier.width(11.dp))
                }
                Column(Modifier.weight(1f)) {
                    StatusPill(visuals = statusVisuals(colors, status), compact = true)
                    Spacer(Modifier.height(6.dp))
                    // The title can use the space above the vertically centered chevron.
                    Text(
                        book?.title ?: "Book details unavailable",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.sp,
                            lineHeightStyle = CoreLibraryLineHeight
                        ), color = colors.ink,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    if (book != null) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            book.author, style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp, lineHeight = 15.sp, lineHeightStyle = CoreLibraryLineHeight
                            ),
                            modifier = Modifier.padding(end = 23.dp),
                            color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        (if (loan.isLender) "With " else "From ") +
                            loan.counterpartyName.ifBlank { "another reader" },
                        modifier = Modifier.padding(end = 23.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp, lineHeight = 16.5.sp, lineHeightStyle = CoreLibraryLineHeight
                        ), color = colors.inkSoft
                    )
                    Row(
                        Modifier.padding(top = 4.dp, end = 23.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (urgency == LoanUrgency.OVERDUE) Icons.Outlined.WarningAmber else Icons.Outlined.Schedule,
                            contentDescription = null, tint = urgencyColor(urgency),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            LoanTimeFormatter.shortRemaining(loan.dueAt, clock),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp, lineHeight = 16.5.sp, lineHeightStyle = CoreLibraryLineHeight
                            ),
                            color = urgencyColor(urgency)
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null, tint = colors.inkMuted,
                modifier = Modifier.size(12.dp).align(Alignment.CenterEnd)
            )
        }
    }
}
