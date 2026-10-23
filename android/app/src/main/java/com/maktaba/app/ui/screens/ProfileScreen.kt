package com.maktaba.app.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.data.BookStatus
import com.maktaba.app.data.FirebaseSession
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.ConfirmationDialog
import com.maktaba.app.ui.components.MaktabaCard
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.theme.BookHavenTheme
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.MinTouchTarget
import java.time.Instant
import java.time.ZoneId

@Composable
fun ProfileScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    var confirmLogout by remember { mutableStateOf(false) }

    val books = LibraryRepository.books
    val owned = books.count { it.status == BookStatus.OWNED }
    val lentOut = books.count { it.status == BookStatus.LENT_OUT }
    val borrowed = books.count { it.status == BookStatus.BORROWED }
    val membershipYear = FirebaseSession.currentUser?.metadata?.creationTimestamp
        ?.takeIf { it > 0 }
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).year }

    if (confirmLogout) {
        ConfirmationDialog(
            title = "Log out?",
            message = "You will need to sign in again to reach your library.",
            confirmLabel = "Log out",
            onConfirm = { FirebaseSession.signOut() },
            onDismiss = { confirmLogout = false }
        )
    }

    MaktabaScaffold(
        selectedTab = BottomNavTab.PROFILE,
        onTabSelected = { navController.navigateToTab(it) },
        topBar = { ScreenTopBar(title = "Profile") }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .padding(horizontal = spacing.gutter)
        ) {
            Spacer(Modifier.height(spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.width(spacing.md))
                Column {
                    Text(
                        FirebaseSession.currentUser?.displayName
                            ?: FirebaseSession.currentUser?.email.orEmpty(),
                        color = colors.ink,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        membershipYear?.let { "Book Haven member since $it" } ?: "Book Haven member",
                        color = colors.inkMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(spacing.lg))
            MaktabaCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = spacing.md)) {
                Row(Modifier.fillMaxWidth()) {
                    ProfileStat("On my shelf", owned, Modifier.weight(1f))
                    ProfileStat("Lent out", lentOut, Modifier.weight(1f))
                    ProfileStat("Borrowed", borrowed, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(spacing.lg))
            ProfileMenuRow(
                label = "Notifications",
                icon = Icons.Filled.Notifications,
                onClick = { navController.navigate(Routes.Notifications.route) }
            )
            ProfileMenuRow(
                label = "Log out",
                icon = Icons.AutoMirrored.Outlined.Logout,
                onClick = { confirmLogout = true }
            )
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = MaktabaTheme.colors
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            color = colors.ink,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            label,
            color = colors.inkMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileMenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(com.maktaba.app.ui.theme.MaktabaShapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.inkSoft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(spacing.md))
        Text(
            label,
            color = colors.ink,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(showBackground = true, name = "Profile")
@Composable
private fun ProfileScreenPreview() {
    BookHavenTheme { ProfileScreen(navController = rememberNavController()) }
}
