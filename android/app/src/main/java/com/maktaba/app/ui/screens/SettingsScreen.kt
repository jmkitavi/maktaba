package com.maktaba.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.google.firebase.FirebaseException
import com.maktaba.app.BuildConfig
import com.maktaba.app.R
import com.maktaba.app.data.AppAppearance
import com.maktaba.app.data.DevicePreferences
import com.maktaba.app.data.LibraryRepository
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.PrimaryButton
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.components.TextActionButton
import com.maktaba.app.ui.components.BottomNavTab
import com.maktaba.app.ui.components.navigateToTab
import com.maktaba.app.ui.components.SoulWelcomeIllustration
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import kotlinx.coroutines.launch
import java.io.IOException
import android.provider.Settings as AndroidSettings

enum class SettingsPage(val title: String) {
    ROOT("Settings"), APPEARANCE("Appearance"), NOTIFICATIONS("Device notifications"), ABOUT("About")
}

enum class DeviceNotificationPermission { ALLOWED, NOT_ALLOWED, NOT_REQUESTED }
enum class SettingsAction { APPEARANCE, NOTIFICATIONS, SHARING, ABOUT, DEVICE_SETTINGS, RETRY_REGISTRATION }

data class SettingsUiState(
    val page: SettingsPage = SettingsPage.ROOT,
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val permission: DeviceNotificationPermission = DeviceNotificationPermission.NOT_REQUESTED,
    val version: String = BuildConfig.VERSION_NAME,
    val saving: Boolean = false,
    val registrationError: String? = null
)

@Composable
fun SettingsScreen(navController: NavHostController, page: SettingsPage = SettingsPage.ROOT) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var saving by remember { mutableStateOf(false) }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permission = when {
        allowed -> DeviceNotificationPermission.ALLOWED
        Build.VERSION.SDK_INT >= 33 && !DevicePreferences.notificationsRequested ->
            DeviceNotificationPermission.NOT_REQUESTED
        else -> DeviceNotificationPermission.NOT_ALLOWED
    }
    SettingsContent(
        state = SettingsUiState(
            page, DevicePreferences.appearance, permission,
            saving = saving, registrationError = LibraryRepository.deviceTokenError
        ),
        snackbarHostState = snackbar,
        onTabSelected = navController::navigateToTab,
        onBack = { navController.popBackStack() },
        onAppearance = { appearance ->
            if (!saving) scope.launch {
                saving = true
                try {
                    DevicePreferences.setAppearance(context, appearance)
                } catch (error: IOException) {
                    snackbar.showSnackbar(error.message ?: "Appearance could not be saved.")
                } finally {
                    saving = false
                }
            }
        },
        onAction = { action ->
            when (action) {
                SettingsAction.APPEARANCE -> navController.navigate(Routes.SettingsAppearance.route)
                SettingsAction.NOTIFICATIONS -> navController.navigate(Routes.SettingsNotifications.route)
                SettingsAction.SHARING -> navController.navigate(Routes.LibrarySharing.route)
                SettingsAction.ABOUT -> navController.navigate(Routes.About.route)
                SettingsAction.RETRY_REGISTRATION -> scope.launch {
                    try {
                        LibraryRepository.retryDeviceNotifications()
                        snackbar.showSnackbar("Notifications registered on this device.")
                    } catch (error: FirebaseException) {
                        snackbar.showSnackbar(error.localizedMessage ?: "Notifications could not be registered.")
                    } catch (error: IOException) {
                        snackbar.showSnackbar(error.localizedMessage ?: "Notifications could not be registered.")
                    } catch (error: IllegalStateException) {
                        snackbar.showSnackbar(error.message ?: "Sign in and try again.")
                    }
                }
                SettingsAction.DEVICE_SETTINGS -> {
                    if (permission == DeviceNotificationPermission.NOT_REQUESTED && Build.VERSION.SDK_INT >= 33) {
                        scope.launch {
                            try {
                                DevicePreferences.markNotificationsRequested(context)
                                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } catch (error: IOException) {
                                snackbar.showSnackbar(error.message ?: "Notification preferences could not be saved.")
                            }
                        }
                    } else {
                        val intent = if (Build.VERSION.SDK_INT >= 26) {
                            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        }
                        try {
                            context.startActivity(intent)
                        } catch (error: ActivityNotFoundException) {
                            scope.launch { snackbar.showSnackbar("Device notification settings are unavailable.") }
                        } catch (error: SecurityException) {
                            scope.launch { snackbar.showSnackbar("Android could not open notification settings.") }
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    onAppearance: (AppAppearance) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onTabSelected: (BottomNavTab) -> Unit = {}
) {
    val colors = MaktabaTheme.colors
    val bodyStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)
    MaktabaScaffold(
        topBar = {
            ScreenTopBar(state.page.title, onBack = onBack, actions = {
                if (state.page != SettingsPage.ABOUT) Image(
                    painterResource(when (state.page) {
                        SettingsPage.ROOT -> R.drawable.illus_loan_shelf
                        SettingsPage.APPEARANCE -> R.drawable.illus_cup_books
                        else -> R.drawable.illus_book_sprig
                    }), null, modifier = Modifier.size(42.dp, 44.dp)
                )
            })
        },
        selectedTab = BottomNavTab.PROFILE,
        onTabSelected = onTabSelected,
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(
                if (state.page == SettingsPage.ROOT || state.page == SettingsPage.ABOUT) 0.dp else 12.dp
            )
        ) {
            when (state.page) {
                SettingsPage.ROOT -> {
                    SettingsSection("THIS DEVICE")
                    SettingsMenuRow(
                        "Appearance",
                        if (state.appearance == AppAppearance.SYSTEM) "Follow device" else state.appearance.label
                    ) { onAction(SettingsAction.APPEARANCE) }
                    SettingsMenuRow("Notifications", "Permission and device settings") { onAction(SettingsAction.NOTIFICATIONS) }
                    SettingsSection("YOUR LIBRARY")
                    SettingsMenuRow("Library Sharing", "Manage your library visibility") { onAction(SettingsAction.SHARING) }
                    SettingsSection("BOOK HAVEN")
                    SettingsMenuRow("About", "App information") { onAction(SettingsAction.ABOUT) }
                    Text(
                        "A quieter home for the books you keep and lend.",
                        style = bodyStyle, color = colors.inkMuted,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
                SettingsPage.APPEARANCE -> {
                    Text("Make yourself at home.", style = MaterialTheme.typography.headlineMedium, color = colors.ink, modifier = Modifier.padding(top = 13.dp))
                    Text("Choose how Book Haven looks on this device.", style = bodyStyle, color = colors.inkMuted)
                    Text("Appearance", style = bodyStyle, color = colors.ink, modifier = Modifier.padding(top = 4.dp))
                    Column {
                        AppAppearance.values().forEach { appearance ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .background(if (appearance == state.appearance) colors.sage else androidx.compose.ui.graphics.Color.Transparent, MaktabaShapes.small)
                                    .selectable(
                                        selected = appearance == state.appearance, enabled = !state.saving,
                                        role = Role.RadioButton, onClick = { onAppearance(appearance) }
                                    ).padding(horizontal = 5.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = appearance == state.appearance, enabled = !state.saving, onClick = null, modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(13.dp))
                                Text(appearance.label, style = bodyStyle, color = colors.ink)
                            }
                            HorizontalDivider(color = colors.divider)
                        }
                    }
                    Text(
                        "Appearance is saved only on this device. Your account preferences are unchanged.",
                        style = bodyStyle, color = colors.accent,
                        modifier = Modifier.fillMaxWidth().background(colors.sage, MaktabaShapes.small)
                            .border(1.dp, colors.divider, MaktabaShapes.small).padding(12.dp)
                    )
                }
                SettingsPage.NOTIFICATIONS -> {
                    Text("Notifications on this device", style = MaterialTheme.typography.headlineMedium, color = colors.ink, modifier = Modifier.padding(top = 13.dp))
                    Column(
                        Modifier.fillMaxWidth().background(colors.sage, MaktabaShapes.medium)
                            .border(1.dp, colors.divider, MaktabaShapes.medium).padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(when (state.permission) {
                            DeviceNotificationPermission.ALLOWED -> "Allowed"
                            DeviceNotificationPermission.NOT_ALLOWED -> "Not allowed"
                            DeviceNotificationPermission.NOT_REQUESTED -> "Not requested"
                        }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (state.permission == DeviceNotificationPermission.ALLOWED) colors.accent else colors.inkMuted,
                            modifier = Modifier.background(
                                if (state.permission == DeviceNotificationPermission.ALLOWED) colors.successContainer else colors.surfaceAlt,
                                MaktabaShapes.small
                            ).padding(horizontal = 7.dp, vertical = 4.dp))
                        Text("Android controls whether this app may show notifications on this device.", style = bodyStyle, color = colors.inkMuted)
                    }
                    Text(
                        if (state.permission == DeviceNotificationPermission.NOT_REQUESTED)
                            "Choose Enable notifications to ask Android for permission."
                        else "Use device settings to review permission, sound and other Android controls.",
                        style = bodyStyle, color = colors.inkMuted
                    )
                    PrimaryButton(
                        if (state.permission == DeviceNotificationPermission.NOT_REQUESTED) "Enable notifications" else "Open device settings",
                        onClick = { onAction(SettingsAction.DEVICE_SETTINGS) }
                    )
                    state.registrationError?.let {
                        Text(it, color = colors.danger, style = MaterialTheme.typography.bodySmall)
                        TextActionButton("Retry notification registration", onClick = { onAction(SettingsAction.RETRY_REGISTRATION) })
                    }
                }
                SettingsPage.ABOUT -> {
                    SoulWelcomeIllustration(R.drawable.illus_onboarding,
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp, bottom = 12.dp).width(238.dp).height(190.dp))
                    Text("A familiar shelf.", style = MaterialTheme.typography.headlineMedium, color = colors.ink)
                    Spacer(Modifier.height(12.dp))
                    Text("A place for your books, and the stories you share.", style = bodyStyle, color = colors.inkMuted)
                    Spacer(Modifier.height(12.dp))
                    SettingsPanel("Android app\n\nVersion ${state.version}")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title, color = MaktabaTheme.colors.accent,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.7.sp),
        modifier = Modifier.padding(top = 23.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsMenuRow(title: String, description: String, onClick: () -> Unit) {
    val colors = MaktabaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = colors.ink, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
            Text(description, color = colors.inkMuted, style = MaterialTheme.typography.labelSmall)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = colors.inkMuted, modifier = Modifier.size(15.dp)
        )
    }
    HorizontalDivider(color = colors.divider)
}

@Composable
private fun SettingsPanel(text: String) {
    val colors = MaktabaTheme.colors
    Text(
        text, color = colors.inkMuted, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().background(colors.sage, MaktabaShapes.medium)
            .border(1.dp, colors.divider, MaktabaShapes.medium).padding(16.dp)
    )
}
