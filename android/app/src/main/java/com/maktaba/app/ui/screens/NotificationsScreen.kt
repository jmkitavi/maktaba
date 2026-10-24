package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.navigation.NavHostController
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.NotificationItem
import com.maktaba.app.ui.components.EmptyState
import com.maktaba.app.ui.components.LoadingState
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.MaktabaBottomNav
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(navController: NavHostController) {
    var filter by rememberSaveable { mutableStateOf(InboxFilter.ALL) }
    var openingId by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    NotificationsContent(
        items = LibraryRepository.notifications.toList(),
        loaded = LibraryRepository.hasLoadedNotifications,
        error = LibraryRepository.notificationsError,
        filter = filter,
        openingId = openingId,
        actionError = actionError,
        now = Instant.now(),
        onBack = { navController.popBackStack() },
        onFilter = { filter = it },
        onRetry = { LibraryRepository.retryLoads() },
        onOpen = open@{ item ->
            if (openingId != null) return@open
            val route = notificationRoute(item)
            if (route == null) {
                actionError = "This notification's destination is no longer available."
                return@open
            }
            openingId = item.id
            actionError = null
            scope.launch {
                try {
                    LibraryRepository.markNotificationRead(item.id)
                    navController.navigate(route)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    actionError = failure.localizedMessage
                        ?: "We could not open this notification. Tap it to try again."
                } finally {
                    openingId = null
                }
            }
        },
        onTab = { navController.navigateToTab(it) }
    )
}

@Composable
fun NotificationsContent(
    items: List<NotificationItem>,
    loaded: Boolean,
    error: String?,
    filter: InboxFilter,
    openingId: String?,
    actionError: String?,
    now: Instant,
    onBack: () -> Unit,
    onFilter: (InboxFilter) -> Unit,
    onRetry: () -> Unit,
    onOpen: (NotificationItem) -> Unit,
    onTab: (BottomNavTab) -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    val gutter = 18.dp
    val status = inboxStatus(loaded, error, items, filter)
    MaktabaScaffold(topBar = { ScreenTopBar("Notifications", onBack = onBack) }) { padding ->
        val direction = LocalLayoutDirection.current
        // The nested bottom bar owns the navigation inset; do not reserve it twice.
        val contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(direction)
        )
        Column(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding).background(colors.background)) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (loaded && error == null) {
                Text(
                    "${items.count { it.isUnread }} unread updates",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.8.sp), color = colors.inkMuted,
                    modifier = Modifier.padding(start = gutter, end = gutter, top = 12.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = gutter)
                    .clip(MaktabaShapes.small).background(colors.surfaceAlt).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                InboxFilter.entries.forEach { value ->
                    Box(
                        Modifier.defaultMinSize(minHeight = 48.dp).clip(MaktabaShapes.small)
                            .background(if (filter == value) colors.surface else colors.surfaceAlt)
                            .selectable(selected = filter == value, role = Role.Tab, onClick = { onFilter(value) })
                            .padding(horizontal = 13.dp)
                            .semantics { contentDescription = if (value == InboxFilter.UNREAD) "Show unread notifications" else "Show all notifications" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (value == InboxFilter.ALL) "All" else "Unread",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (filter == value) colors.primary else colors.ink)
                    }
                }
            }
            if (actionError != null) {
                Box(Modifier.padding(horizontal = gutter, vertical = spacing.xs)) {
                    LoanFeedback(actionError, error = true)
                }
            }
            when (status) {
                InboxStatus.LOADING -> LoadingState("Loading your notifications")
                InboxStatus.ERROR -> Column(
                    Modifier.fillMaxWidth().padding(spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    LoanIntro("Could Not Load Notifications", error ?: "Please try again.")
                    PrimaryButton("Try Again", onClick = onRetry)
                }
                InboxStatus.EMPTY, InboxStatus.NO_UNREAD -> EmptyState(
                    modifier = Modifier.weight(1f),
                    title = if (status == InboxStatus.NO_UNREAD) "No unread notifications" else "You Are All Caught Up",
                    message = if (status == InboxStatus.NO_UNREAD) "Switch to All to see your earlier updates."
                    else "Reminders about due dates and returns will arrive here."
                )
                InboxStatus.READY -> {
                    val visible = if (filter == InboxFilter.UNREAD) items.filter { it.isUnread } else items
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = gutter, end = gutter, top = 10.dp, bottom = 26.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visible, key = { it.id }) { item ->
                            NotificationRow(
                                item = item,
                                enabled = openingId == null,
                                opening = openingId == item.id,
                                now = now,
                                onClick = { onOpen(item) }
                            )
                        }
                        item(key = "footer") { Spacer(Modifier.height(spacing.lg)) }
                    }
                }
            }
        }
        MaktabaBottomNav(selected = null, onSelect = onTab)
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    enabled: Boolean,
    opening: Boolean,
    now: Instant,
    onClick: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val rowShape = RoundedCornerShape(11.dp)
    val lineHeight = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
    Column(
        Modifier.fillMaxWidth().clip(rowShape)
            .background(if (item.isUnread) colors.surfaceAlt else colors.surface)
            .border(BorderStroke(1.dp, colors.divider), rowShape)
            .drawBehind {
                if (item.isUnread) drawLine(colors.primary, Offset(1.5.dp.toPx(), 0f),
                    Offset(1.5.dp.toPx(), size.height), strokeWidth = 3.dp.toPx())
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = if (item.isUnread) 16.dp else 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
            .semantics(mergeDescendants = true) {
                val createdLabel = item.createdAt?.let { created ->
                    val date = created.atZone(ZoneId.systemDefault())
                    if (date.toLocalDate() == now.atZone(ZoneId.systemDefault()).toLocalDate()) "Today"
                    else date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
                }
                contentDescription = "${if (item.isUnread) "Unread. " else "Read. "}${item.title}. ${item.message}${createdLabel?.let { ". $it" }.orEmpty()}${if (opening) ". Opening" else ""}"
            },
    ) {
            Text(
                if (item.isUnread) "Unread" else "Read",
                color = if (item.type == "lender_overdue") colors.warning else colors.inkMuted,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp, lineHeight = 15.sp, lineHeightStyle = lineHeight
                ),
                modifier = Modifier.clip(RoundedCornerShape(5.dp))
                    .background(if (item.type == "lender_overdue") colors.warningContainer else colors.surfaceAlt)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            )
        Spacer(Modifier.height(4.dp))
        Text(item.title, color = colors.ink, style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp, lineHeight = 21.45.sp, fontWeight = FontWeight.SemiBold,
            lineHeightStyle = lineHeight
        ))
        Spacer(Modifier.height(4.dp))
        Text(item.message, color = colors.inkMuted, style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 11.sp, lineHeight = 18.15.sp, lineHeightStyle = lineHeight
        ))
        if (opening) Text("Opening…", color = colors.inkSoft, style = MaterialTheme.typography.labelSmall)
    }
}
