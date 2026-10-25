package com.maktaba.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.maktaba.app.R
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.CollectionsRepository
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.data.LoadState
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun ProfileScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val books = LibraryRepository.books
    val sharing = CollectionsRepository.sharingSettings
    val settings = (sharing as? LoadState.Ready)?.value
    val user = FirebaseSession.currentUser
    val state = LibraryProfileState(
        name = user?.displayName?.takeIf(String::isNotBlank)
            ?: user?.email.orEmpty(),
        memberSince = user?.metadata?.creationTimestamp?.takeIf { it > 0 }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year },
        shelfCount = books.count { it.status == BookStatus.OWNED },
        lentCount = books.count { it.status == BookStatus.LENT_OUT },
        borrowedCount = books.count { it.status == BookStatus.BORROWED },
        libraryLoaded = LibraryRepository.hasLoadedLibrary && LibraryRepository.hasLoadedLoans,
        libraryError = LibraryRepository.libraryError ?: LibraryRepository.loansError,
        sharingLoaded = settings != null,
        shared = settings?.libraryShared == true,
        sharingError = when (sharing) {
            is LoadState.Error -> sharing.cause.localizedMessage ?: "Sharing settings could not load."
            LoadState.NotFound -> "Sharing settings are unavailable."
            else -> null
        }
    )
    ProfileContent(
        state = state,
        onSharing = { navController.navigate(Routes.LibrarySharing.route) },
        onSettings = { navController.navigate(Routes.Settings.route) },
        onNotifications = { navController.navigate(Routes.Notifications.route) },
        onLogout = { navController.navigate(Routes.Logout.route) },
        onTab = { navController.navigateToTab(it) },
        onRetryLibrary = LibraryRepository::retryLoads,
        onRetrySharing = {
            scope.launch { runCatching { CollectionsRepository.refreshSharingSettings() } }
        }
    )
}

@Composable
fun ProfileContent(
    state: LibraryProfileState,
    onSharing: () -> Unit,
    onSettings: () -> Unit,
    onNotifications: () -> Unit,
    onLogout: () -> Unit,
    onTab: (BottomNavTab) -> Unit,
    onRetryLibrary: () -> Unit,
    onRetrySharing: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    MaktabaScaffold(
        selectedTab = BottomNavTab.PROFILE,
        onTabSelected = onTab,
        topBar = {
            ScreenTopBar(
                title = "Profile", branded = true,
                illustrationRes = R.drawable.illus_home_icon
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
        ) {
            Spacer(Modifier.height(21.dp))
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape, color = colors.terracotta,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.size(62.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            state.initials.ifBlank { "?" },
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp),
                            color = colors.terraAction
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.name, color = colors.ink, style = MaterialTheme.typography.headlineMedium.copy(
                            lineHeight = 28.sp, letterSpacing = (-0.5).sp
                        ),
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        state.memberSince?.let { "Book Haven member since $it" } ?: "Book Haven member",
                        color = colors.inkMuted, style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp, lineHeight = 17.6.sp
                        )
                    )
                }
            }
            Spacer(Modifier.height(27.dp))
            Surface(
                shape = MaktabaShapes.medium, color = colors.sage,
                border = BorderStroke(1.dp, colors.divider)
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 22.dp)) {
                    val countsReady = state.libraryLoaded && state.libraryError == null
                    ProfileStat("On My Shelf", state.shelfCount.takeIf { countsReady }, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(52.dp).background(colors.divider))
                    ProfileStat("Lent Out", state.lentCount.takeIf { countsReady }, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(52.dp).background(colors.divider))
                    ProfileStat("Borrowed", state.borrowedCount.takeIf { countsReady }, Modifier.weight(1f))
                }
            }
            if (state.libraryError != null) {
                Text(state.libraryError, color = colors.danger, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetryLibrary) { Text("Retry library counts", color = colors.primary) }
            } else if (!state.libraryLoaded) {
                Text("Loading library counts…", color = colors.inkMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(30.dp))
            Surface(
                shape = MaktabaShapes.medium, color = colors.ochre,
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onSharing)
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(colors.sage),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.shared && state.sharingLoaded) Icons.Filled.People else Icons.Outlined.Lock,
                            contentDescription = null, tint = colors.primary, modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                state.sharingError != null -> "Sharing settings unavailable"
                                !state.sharingLoaded -> "Loading sharing settings…"
                                state.shared -> "Your library is shared"
                                else -> "Your library is private"
                            },
                            color = colors.ink, style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp, lineHeight = 22.1.sp,
                                lineHeightStyle = CoreLibraryLineHeight
                            )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            state.sharingError ?: when {
                                !state.sharingLoaded -> "Checking your library visibility."
                                state.shared -> "Visible to signed-in Book Haven readers"
                                else -> "Only you can see your shelf"
                            },
                            color = colors.inkMuted, style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp, lineHeight = 16.sp,
                                lineHeightStyle = CoreLibraryLineHeight
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                if (state.sharingLoaded && !state.shared) "Share My Library"
                                else "Library Sharing",
                                color = colors.terraAction, style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp, lineHeight = 12.sp,
                                    lineHeightStyle = CoreLibraryLineHeight
                                )
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null, tint = colors.terraAction,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        if (state.sharingError != null) {
                            TextButton(onClick = onRetrySharing) { Text("Try again", color = colors.primary) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(25.dp))
            Text(
                "YOUR ACCOUNT", color = colors.inkMuted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp
                )
            )
            Spacer(Modifier.height(13.dp))
            HorizontalDivider(color = colors.divider)
            ProfileMenuRow(
                "Notifications", "Updates on your shared books",
                Icons.Filled.NotificationsNone, colors.ochre, colors.warning, onNotifications
            )
            ProfileMenuRow(
                "Settings", "App preferences", Icons.Filled.Settings,
                colors.sage, colors.primary, onSettings
            )
            ProfileMenuRow(
                "Log Out", "Sign out of Book Haven",
                Icons.AutoMirrored.Outlined.Logout, colors.terracotta, colors.terraAction,
                onLogout
            )
            Spacer(Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: Int?, modifier: Modifier = Modifier) {
    val colors = MaktabaTheme.colors
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value?.toString() ?: "—", color = colors.primary,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 28.sp)
        )
        Spacer(Modifier.height(9.dp))
        Text(
            label, color = colors.inkMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 14.4.sp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileMenuRow(
    label: String,
    supporting: String,
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 78.dp)
                .clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, contentDescription = null, tint = iconTint,
                modifier = Modifier.clip(MaktabaShapes.medium).background(iconBackground)
                    .padding(9.dp).size(18.dp)
            )
            Spacer(Modifier.width(spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    label, color = colors.ink,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium
                    )
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    supporting, color = colors.inkMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 16.sp)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(13.dp)
            )
        }
        HorizontalDivider(color = colors.divider)
    }
}
