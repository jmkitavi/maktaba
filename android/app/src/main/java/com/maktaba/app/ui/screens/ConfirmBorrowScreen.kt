package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BookCoverImage
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.TextActionButton
import com.maktaba.app.ui.components.UnavailableState
import com.maktaba.app.ui.theme.MaktabaAppTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.util.LoanTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ConfirmBorrowScreen(navController: NavHostController, inviteCode: String) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resolving by remember(inviteCode) { mutableStateOf(true) }
    var resolvedBookId by remember(inviteCode) {
        mutableStateOf(LibraryRepository.bookIdForCode(inviteCode))
    }

    LaunchedEffect(inviteCode) {
        if (resolvedBookId == null) {
            runCatching { LibraryRepository.resolveLoanCode(inviteCode) }
                .onSuccess { resolvedBookId = it }
                .onFailure {
                    error = it.localizedMessage ?: "This borrowing invitation is unavailable."
                }
        }
        resolving = false
    }

    if (resolving) {
        Box(
            Modifier.fillMaxSize().background(colors.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = colors.primary)
        }
        return
    }

    val bookId = resolvedBookId
    val book = bookId?.let(LibraryRepository::bookById)
    val invitation = bookId?.let(LibraryRepository::activeLoanFor)
    if (book == null || invitation == null) {
        UnavailableState(
            title = "Invitation unavailable",
            message = error ?: "We could not find this invitation. Ask the lender for a new code.",
            onBack = navController::popBackStack,
            onLibrary = { navController.navigate(Routes.HomeLibrary.route) { popUpTo(0) } }
        )
        return
    }

    val lenderName = invitation.counterpartyName
    val dueDate = LoanTimeFormatter.formatDate(invitation.dueAt)

    MaktabaScaffold(
        topBar = {
            ScreenTopBar(title = "Confirm borrow", onBack = { navController.popBackStack() })
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
        ) {
            Spacer(Modifier.height(spacing.sm))
            Text(
                "Confirm you have the book",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() }
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                "Only confirm once $lenderName has physically handed it to you. This starts " +
                    "the loan and its return date.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(spacing.md))
            MaktabaCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    BookCoverImage(
                        book = book,
                        contentScale = ContentScale.Crop,
                        decorative = true,
                        modifier = Modifier
                            .width(100.dp)
                            .aspectRatio(0.68f)
                            .clip(MaktabaShapes.cover)
                    )
                    Spacer(Modifier.width(spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                        Spacer(Modifier.height(spacing.sm))
                        IconDetail(Icons.Filled.Person, "Lender", lenderName)
                    }
                }
            }

            Spacer(Modifier.height(spacing.sm))
            MaktabaCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Agreed return date",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMuted
                        )
                        Text(
                            dueDate,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.ink
                        )
                    }
                }
            }

            Spacer(Modifier.height(spacing.lg))
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger)
                Spacer(Modifier.height(spacing.xs))
            }
            PrimaryButton(
                text = "I have the book",
                loading = loading,
                enabled = !loading,
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    if (loading) return@PrimaryButton
                    loading = true
                    error = null
                    scope.launch {
                        runCatching {
                            LibraryRepository.confirmBorrow(book.id, lenderName, dueDate)
                        }
                            .onSuccess {
                                navController.navigate(Routes.ActiveLoan.createRoute(book.id))
                            }
                            .onFailure {
                                error = it.localizedMessage ?: "We could not accept this loan."
                            }
                        loading = false
                    }
                }
            )
            Spacer(Modifier.height(spacing.xxs))
            TextActionButton(text = "Not yet", onClick = { navController.popBackStack() })

            Spacer(Modifier.height(spacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaktabaShapes.small)
                    .background(colors.surfaceAlt)
                    .padding(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(spacing.xs))
                Text(
                    "We will remind you before the due date so you never miss a return.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft
                )
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun IconDetail(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    val colors = MaktabaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {}
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
            Text(value, style = MaterialTheme.typography.labelMedium, color = colors.ink)
        }
    }
}

@Preview(showBackground = true, name = "Confirm borrow")
@Composable
private fun ConfirmBorrowScreenPreview() {
    MaktabaAppTheme {
        ConfirmBorrowScreen(navController = rememberNavController(), inviteCode = "ABCD-1234")
    }
}
