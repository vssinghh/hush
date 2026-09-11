package com.hush.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hush.app.domain.model.RuleAction
import com.hush.app.ui.components.HushHeader
import com.hush.app.ui.components.QuietSurface
import com.hush.app.ui.theme.*
import com.hush.app.R

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

    // Refresh once when the screen first appears (tab navigation does not
    // trigger ON_RESUME on the host activity)
    LaunchedEffect(Unit) { viewModel.refreshPermissions() }

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
        modifier = modifier.testTag("settings_screen"),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            HushHeader(
                title = "Settings",
                subtitle = "Service status & preferences"
            )

            // ── Section: Service Status ──
            SectionLabel("SERVICE STATUS")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    accent = SageGreen,
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
                SettingsRow(
                    icon = Icons.Filled.Mic,
                    accent = SlateBlue,
                    title = "Voice Input",
                    subtitle = "Control Hush with voice commands",
                    trailing = {
                        StatusBadge(
                            isActive = isVoiceActive,
                            modifier = Modifier.testTag("settings_voice_status")
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section: Appearance ──
            SectionLabel("APPEARANCE")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    accent = PlumMist,
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
                                contentDescription = stringResource(R.string.show_theme),
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
                            .padding(start = 72.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionChip(
                            label = "Light Theme",
                            isSelected = themeOption == "Light Theme",
                            onClick = {
                                themeOption = "Light Theme"
                                prefs.edit().putString("theme_option", "Light Theme").apply()
                                showThemeMenu = false
                            },
                            modifier = Modifier.testTag("settings_theme_light_option")
                        )
                        OptionChip(
                            label = "Dark Theme",
                            isSelected = themeOption == "Dark Theme",
                            onClick = {
                                themeOption = "Dark Theme"
                                prefs.edit().putString("theme_option", "Dark Theme").apply()
                                showThemeMenu = false
                            },
                            modifier = Modifier.testTag("settings_theme_dark_option")
                        )
                        OptionChip(
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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section: Data ──
            SectionLabel("DATA")
            SettingsGroup {
                SettingsRow(
                    icon = Icons.Filled.AutoDelete,
                    accent = SlateBlue,
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
                                contentDescription = stringResource(R.string.show_retention),
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
                            .padding(start = 72.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptionChip(
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
                        OptionChip(
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
                        OptionChip(
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

                // Rule Tester
                var showRuleTester by remember { mutableStateOf(false) }
                var selectedAppIndex by remember { mutableIntStateOf(-1) }
                var testTitle by remember { mutableStateOf("") }
                var testText by remember { mutableStateOf("") }
                var testSender by remember { mutableStateOf("") }
                var showAppDropdown by remember { mutableStateOf(false) }
                val installedApps by viewModel.installedApps.collectAsState()
                val testResult by viewModel.testResult.collectAsState()

                SettingsRow(
                    icon = Icons.Filled.PlayArrow,
                    accent = HarborTeal,
                    title = "Rule Tester",
                    subtitle = "Simulate a notification to test your rules",
                    onClick = {
                        showRuleTester = !showRuleTester
                        if (!showRuleTester) viewModel.clearTestResult()
                    },
                    modifier = Modifier.testTag("settings_rule_tester"),
                    trailing = {
                        Icon(
                            imageVector = if (showRuleTester)
                                Icons.Filled.KeyboardArrowDown
                            else
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.show_ruletester),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                if (showRuleTester) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // App selector
                        Box {
                            OutlinedTextField(
                                value = if (selectedAppIndex >= 0 && selectedAppIndex < installedApps.size)
                                    installedApps[selectedAppIndex].displayName
                                else "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("App") },
                                placeholder = { Text("Select an app...") },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAppDropdown = true },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            // Invisible clickable overlay
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showAppDropdown = true }
                            )
                            DropdownMenu(
                                expanded = showAppDropdown,
                                onDismissRequest = { showAppDropdown = false },
                                modifier = Modifier.heightIn(max = 300.dp)
                            ) {
                                installedApps.forEachIndexed { index, app ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = app.displayName,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedAppIndex = index
                                            showAppDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Title field
                        OutlinedTextField(
                            value = testTitle,
                            onValueChange = { testTitle = it },
                            label = { Text("Title") },
                            placeholder = { Text("e.g. Promotion Alert") },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Text field
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            label = { Text("Text / Body") },
                            placeholder = { Text("e.g. 50% off today only!") },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Sender field
                        OutlinedTextField(
                            value = testSender,
                            onValueChange = { testSender = it },
                            label = { Text("Sender") },
                            placeholder = { Text("e.g. Mom, Bob") },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // Evaluate button
                        Button(
                            onClick = {
                                if (selectedAppIndex >= 0 && selectedAppIndex < installedApps.size) {
                                    val app = installedApps[selectedAppIndex]
                                    viewModel.testRule(
                                        packageName = app.packageName,
                                        appName = app.displayName,
                                        title = testTitle,
                                        text = testText,
                                        sender = testSender
                                    )
                                }
                            },
                            enabled = selectedAppIndex >= 0,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Evaluate Rules", fontWeight = FontWeight.SemiBold)
                        }

                        // Result display
                        testResult?.let { result ->
                            val resultColor = when (result.action) {
                                RuleAction.BLOCK -> EmberRed
                                RuleAction.MUTE -> DuskGold
                                RuleAction.ALLOW -> SageGreen
                            }
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = resultColor.copy(alpha = 0.10f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Verdict: ${result.action.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = resultColor
                                    )
                                    Text(
                                        text = "A notification from ${result.appName} with these properties would be ${result.action.name.lowercase()}ed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                SettingsDivider()

                // Reset Onboarding
                SettingsRow(
                    icon = Icons.Filled.RestartAlt,
                    accent = EmberRed,
                    title = "Reset Onboarding",
                    subtitle = "Re-run the first-time setup wizard",
                    onClick = onResetOnboarding,
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.rerun_wizard),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }

            // ── Version Footer ──
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Hush v1.0",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = HushSerif,
                    fontStyle = FontStyle.Italic
                ),
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
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
    )
}

/** A hairline-bordered island grouping related settings rows. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    QuietSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    accent: Color,
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Soft-washed icon tile
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // too generalist for a contentDescription
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

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
    val color = if (isActive) SageGreen else EmberRed
    val label = if (isActive) "Active" else "Inactive"
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        // Read the badge as a single element (also lets tests query its text)
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun OptionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
