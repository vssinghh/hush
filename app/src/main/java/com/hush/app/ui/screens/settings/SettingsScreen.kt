package com.hush.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState

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
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Badges Section
            Text("Service Status", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notification Interception", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isNotificationActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isNotificationActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("settings_notification_status")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Voice Input", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isVoiceActive) "Active" else "Inactive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isVoiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("settings_voice_status")
                        )
                    }
                }
            }

            HorizontalDivider()

            // Theme Preference
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeMenu = !showThemeMenu }
                    .testTag("settings_theme_pref")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Theme Option")
                    Text(themeOption, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (showThemeMenu) {
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            themeOption = "Light Theme"
                            prefs.edit().putString("theme_option", "Light Theme").apply()
                            showThemeMenu = false
                        },
                        modifier = Modifier.testTag("settings_theme_light_option")
                    ) {
                        Text("Light Theme")
                    }
                    Button(
                        onClick = {
                            themeOption = "Dark Theme"
                            prefs.edit().putString("theme_option", "Dark Theme").apply()
                            showThemeMenu = false
                        },
                        modifier = Modifier.testTag("settings_theme_dark_option")
                    ) {
                        Text("Dark Theme")
                    }
                    Button(
                        onClick = {
                            themeOption = "System Default"
                            prefs.edit().putString("theme_option", "System Default").apply()
                            showThemeMenu = false
                        },
                        modifier = Modifier.testTag("settings_theme_system_option")
                    ) {
                        Text("System Default")
                    }
                }
            }

            HorizontalDivider()

            // Retention Preference
            Text("Data Retention", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        showRetentionMenu = !showRetentionMenu 
                    }
                    .testTag("settings_retention_pref")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("History Retention")
                    Text(retentionPolicy, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (showRetentionMenu) {
                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            retentionPolicy = "7 Days"
                            prefs.edit().putString("retention_policy", "7 Days").apply()
                            showRetentionMenu = false
                            viewModel.pruneDatabase("7 Days")
                        },
                        modifier = Modifier.testTag("settings_retention_7_days")
                    ) {
                        Text("7 Days")
                    }
                    Button(
                        onClick = {
                            retentionPolicy = "30 Days"
                            prefs.edit().putString("retention_policy", "30 Days").apply()
                            showRetentionMenu = false
                            viewModel.pruneDatabase("30 Days")
                        },
                        modifier = Modifier.testTag("settings_retention_30_days")
                    ) {
                        Text("30 Days")
                    }
                    Button(
                        onClick = {
                            retentionPolicy = "90 Days"
                            prefs.edit().putString("retention_policy", "90 Days").apply()
                            showRetentionMenu = false
                            viewModel.pruneDatabase("90 Days")
                        },
                        modifier = Modifier.testTag("settings_retention_90_days")
                    ) {
                        Text("90 Days")
                    }
                }
            }

            HorizontalDivider()

            Text("Developer Options", style = MaterialTheme.typography.titleMedium)
            
            Button(
                onClick = onResetOnboarding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Onboarding Flow")
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Hush Version 1.0 (MVP Skeleton)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
