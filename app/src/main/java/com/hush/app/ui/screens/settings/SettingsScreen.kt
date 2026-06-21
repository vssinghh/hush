package com.hush.app.ui.screens.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import com.hush.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onResetOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE) }
    val isNotificationActive by viewModel.isNotificationActive.collectAsState()
    val isVoiceActive by viewModel.isVoiceActive.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var themeOption by remember {
        mutableStateOf(prefs.getString("theme_option", "System Default") ?: "System Default")
    }
    var showThemeMenu by remember { mutableStateOf(false) }

    var retentionPolicy by remember {
        mutableStateOf(prefs.getString("retention_policy", "30 Days") ?: "30 Days")
    }
    var showRetentionMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Section: Service Status ──
            SectionLabel("SERVICE STATUS")

            // Notification Interception
            SettingsRow(
                icon = Icons.Filled.Notifications,
                iconTint = Color.White,
                iconBackground = AccentGreen,
                title = "Notification Interception",
                subtitle = "Intercept and classify incoming notifications",
                trailing = {
                    StatusBadge(
                        isActive = isNotificationActive,
                        modifier = Modifier.testTag("settings_notification_status")
                    )
                }
            )
            SettingsDivider()

            // Voice Input
            SettingsRow(
                icon = Icons.Filled.Settings,
                iconTint = Color.White,
                iconBackground = AccentGreen,
                title = "Voice Input",
                subtitle = "Control Hush with voice commands",
                trailing = {
                    StatusBadge(
                        isActive = isVoiceActive,
                        modifier = Modifier.testTag("settings_voice_status")
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section: Appearance ──
            SectionLabel("APPEARANCE")

            SettingsRow(
                icon = Icons.Filled.Star,
                iconTint = Color.White,
                iconBackground = AccentPurple,
                title = "Theme",
                subtitle = "Choose light, dark, or system theme",
                onClick = { showThemeMenu = !showThemeMenu },
                modifier = Modifier.testTag("settings_theme_pref"),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = themeOption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // Theme selector (inline options)
            if (showThemeMenu) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 80.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionChip(
                        label = "Light Theme",
                        isSelected = themeOption == "Light Theme",
                        onClick = {
                            themeOption = "Light Theme"
                            prefs.edit().putString("theme_option", "Light Theme").apply()
                            showThemeMenu = false
                        },
                        modifier = Modifier.testTag("settings_theme_light_option")
                    )
                    ThemeOptionChip(
                        label = "Dark Theme",
                        isSelected = themeOption == "Dark Theme",
                        onClick = {
                            themeOption = "Dark Theme"
                            prefs.edit().putString("theme_option", "Dark Theme").apply()
                            showThemeMenu = false
                        },
                        modifier = Modifier.testTag("settings_theme_dark_option")
                    )
                    ThemeOptionChip(
                        label = "System Default",
                        isSelected = themeOption == "System Default",
                        onClick = {
                            themeOption = "System Default"
                            prefs.edit().putString("theme_option", "System Default").apply()
                            showThemeMenu = false
                        },
                        modifier = Modifier.testTag("settings_theme_system_option")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section: Data ──
            SectionLabel("DATA")

            SettingsRow(
                icon = Icons.Filled.Delete,
                iconTint = Color.White,
                iconBackground = AccentBlue,
                title = "History Retention",
                subtitle = "How long to keep notification history",
                onClick = { showRetentionMenu = !showRetentionMenu },
                modifier = Modifier.testTag("settings_retention_pref"),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = retentionPolicy,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            // Retention selector (inline options)
            if (showRetentionMenu) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 80.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOptionChip(
                        label = "7 Days",
                        isSelected = retentionPolicy == "7 Days",
                        onClick = {
                            retentionPolicy = "7 Days"
                            prefs.edit().putString("retention_policy", "7 Days").apply()
                            showRetentionMenu = false
                            viewModel.pruneDatabase("7 Days")
                        },
                        modifier = Modifier.testTag("settings_retention_7_days")
                    )
                    ThemeOptionChip(
                        label = "30 Days",
                        isSelected = retentionPolicy == "30 Days",
                        onClick = {
                            retentionPolicy = "30 Days"
                            prefs.edit().putString("retention_policy", "30 Days").apply()
                            showRetentionMenu = false
                            viewModel.pruneDatabase("30 Days")
                        },
                        modifier = Modifier.testTag("settings_retention_30_days")
                    )
                    ThemeOptionChip(
                        label = "90 Days",
                        isSelected = retentionPolicy == "90 Days",
                        onClick = {
                            retentionPolicy = "90 Days"
                            prefs.edit().putString("retention_policy", "90 Days").apply()
                            showRetentionMenu = false
                            viewModel.pruneDatabase("90 Days")
                        },
                        modifier = Modifier.testTag("settings_retention_90_days")
                    )
                }
            }

            SettingsDivider()

            // Send Test Notification
            SettingsRow(
                icon = Icons.Filled.PlayArrow,
                iconTint = Color.White,
                iconBackground = AccentTeal,
                title = "Send Test Notification",
                subtitle = "Fire a test notification to verify blocking",
                onClick = {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channelId = "hush_test_channel"
                    if (nm.getNotificationChannel(channelId) == null) {
                        nm.createNotificationChannel(
                            NotificationChannel(channelId, "Hush Test", NotificationManager.IMPORTANCE_HIGH)
                        )
                    }
                    val notification = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Test: Mom")
                        .setContentText("Hey, are you coming for dinner tonight?")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build()
                    nm.notify(System.currentTimeMillis().toInt(), notification)
                },
                modifier = Modifier.testTag("settings_test_notification"),
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            SettingsDivider()

            // Reset Onboarding
            SettingsRow(
                icon = Icons.Filled.Warning,
                iconTint = Color.White,
                iconBackground = AccentRed,
                title = "Reset Onboarding",
                subtitle = "Re-run the first-time setup wizard",
                onClick = onResetOnboarding,
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            // ── Version Footer ──
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Hush v1.0",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
        }
    }
}

// ─── Reusable components ────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored circular icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Trailing content (badge, chevron, or value)
        trailing()
    }
}

@Composable
private fun StatusBadge(isActive: Boolean, modifier: Modifier = Modifier) {
    val bgColor = if (isActive) AccentGreenLight else AccentRedLight
    val textColor = if (isActive) AccentGreen else AccentRed
    val label = if (isActive) "Active" else "Inactive"
    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp, end = 24.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun ThemeOptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) AccentPurpleLight else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) AccentPurple else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
