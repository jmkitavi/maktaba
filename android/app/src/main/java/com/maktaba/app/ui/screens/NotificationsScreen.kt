package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.NotificationItem
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.EmptyState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme

@Composable
fun NotificationsScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val notifications = LibraryRepository.notifications

    MaktabaScaffold(
        topBar = {
            ScreenTopBar(title = "Notifications", onBack = { navController.popBackStack() })
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            if (notifications.isEmpty()) {
                EmptyState(
                    modifier = Modifier.weight(1f),
                    title = "You are all caught up",
                    message = "Reminders about due dates and returns will arrive here."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        NotificationRow(
                            item = item,
                            onClick = {
                                if (item.bookId.isBlank()) return@NotificationRow
                                LibraryRepository.markNotificationRead(item.id)
                                navController.navigate(routeFor(item))
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun routeFor(item: NotificationItem): String = when (item.type) {
    "loan_invite_accepted", "loan_reminder" -> Routes.ActiveLoan.createRoute(item.bookId)
    "return_requested" -> Routes.ReturnConfirmation.createRoute(item.bookId)
    "return_confirmed" ->
        Routes.BookDetail.createRoute(item.catalogBookId.ifBlank { item.bookId })
    else -> Routes.ReturnReminder.createRoute(item.bookId)
}

@Composable
private fun NotificationRow(item: NotificationItem, onClick: () -> Unit) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing

    // Overdue and due-soon notices must not be typeset like an ordinary update.
    val (icon: ImageVector, tint: Color) = when (item.type) {
        "lender_overdue" -> Icons.Filled.ErrorOutline to colors.danger
        "borrower_due_today", "borrower_due_soon", "loan_reminder" ->
            Icons.Filled.ScheduleSend to colors.warning
        "return_confirmed" -> Icons.Filled.CheckCircle to colors.success
        "loan_invite_accepted", "return_requested" -> Icons.Filled.SwapHoriz to colors.primary
        else -> Icons.Filled.NotificationsNone to colors.inkSoft
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaktabaShapes.medium)
            .background(if (item.isUnread) colors.surfaceAlt else colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    if (item.isUnread) append("Unread. ")
                    append(item.title)
                    append(". ")
                    append(item.message)
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = colors.ink,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.message,
                color = colors.inkMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (item.isUnread) {
            Spacer(Modifier.width(spacing.xs))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
            )
        }
    }
}

@Preview(showBackground = true, name = "Notifications")
@Composable
private fun NotificationsScreenPreview() {
    BookHavenTheme { NotificationsScreen(navController = rememberNavController()) }
}
