package com.hush.app.ui.screens.onboarding

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) }
    var showBatteryWarning by remember { mutableStateOf(false) }

    // Refresh permissions whenever the user returns to the app (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (viewModel.isNotificationPermissionRequested && !viewModel.hasNotificationAccess) {
                    viewModel.denyNotificationAccess()
                }
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Mic Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
    }

    // Battery Optimization Launcher
    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.refreshPermissions()
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            showBatteryWarning = true
        }
    }

    // Warning Dialog for Battery Optimization Denial
    if (showBatteryWarning) {
        AlertDialog(
            onDismissRequest = { showBatteryWarning = false },
            title = { Text("Keep App Alive") },
            text = { Text("Hush works best when exempted from battery restrictions.") },
            confirmButton = {
                Button(
                    onClick = { showBatteryWarning = false },
                    modifier = Modifier.testTag("onboarding_battery_warning_dismiss")
                ) {
                    Text("Dismiss")
                }
            },
            modifier = Modifier.testTag("onboarding_battery_warning")
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Welcome to Hush") }
            )
        },
        modifier = modifier.testTag("onboarding_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "OnboardingStepTransition",
                modifier = Modifier.weight(1f)
            ) { step ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (step) {
                        0 -> WelcomeStep(onNext = { currentStep = 1 })
                        1 -> PermissionsStep(
                            hasNotificationAccess = viewModel.hasNotificationAccess && !viewModel.isNotificationAccessDenied,
                            hasMicrophonePermission = viewModel.hasMicrophonePermission,
                            isBatteryExempt = viewModel.isBatteryExempt,
                            onRequestNotification = {
                                viewModel.requestNotificationAccess(context)
                            },
                            onRequestMicrophone = {
                                viewModel.requestMicrophonePermission(micPermissionLauncher)
                            },
                            onRequestBattery = {
                                viewModel.requestBatteryExemption(batteryLauncher)
                            },
                            onRequestDenyNotification = {
                                viewModel.denyNotificationAccess()
                            },
                            onNext = { currentStep = 2 },
                            canProceed = viewModel.hasNotificationAccess && !viewModel.isNotificationAccessDenied,
                            showDenyRationale = viewModel.isNotificationAccessDenied
                        )
                        2 -> AICoreStep(
                            onComplete = onOnboardingComplete
                        )
                    }
                }
            }

            // Step Indicator dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                repeat(3) { index ->
                    val color = if (index == currentStep) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .padding(2.dp)
                            .weight(1f, false)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = color,
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.WelcomeStep(onNext: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = "Privacy-first notification filtering",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Hush runs fully on-device, using Gemini Nano to understand your commands. Block, allow, or mute notifications without compromising your data.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.testTag("onboarding_next_button")
        ) {
            Text("Get Started")
        }
    }
}

@Composable
fun ColumnScope.PermissionsStep(
    hasNotificationAccess: Boolean,
    hasMicrophonePermission: Boolean,
    isBatteryExempt: Boolean,
    onRequestNotification: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestDenyNotification: () -> Unit,
    onNext: () -> Unit,
    canProceed: Boolean,
    showDenyRationale: Boolean
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = "Configure Permissions",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        // 1. Notification Access
        PermissionRow(
            title = "Notification Interception",
            description = "Allows Hush to read and filter notifications. (Mandatory)",
            isGranted = hasNotificationAccess,
            onRequest = onRequestNotification,
            buttonTag = "onboarding_grant_notification"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Microphone
        PermissionRow(
            title = "Microphone Access",
            description = "Enables natural language voice commands. (Optional)",
            isGranted = hasMicrophonePermission,
            onRequest = onRequestMicrophone,
            buttonTag = "onboarding_grant_mic"
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Battery Exclusion
        PermissionRow(
            title = "Keep App Alive",
            description = "Exempts Hush from battery restrictions so it runs in the background. (Optional)",
            isGranted = isBatteryExempt,
            onRequest = onRequestBattery,
            buttonTag = "onboarding_ignore_battery"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNext,
            enabled = canProceed,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("onboarding_next_button")
        ) {
            Text("Continue")
        }

        AnimatedVisibility(
            visible = showDenyRationale,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500)),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Grant notification access to continue",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("onboarding_deny_rationale")
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    buttonTag: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = if (buttonTag != null) Modifier.testTag(buttonTag) else Modifier
                ) {
                    Text("Grant", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ColumnScope.AICoreStep(onComplete: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.weight(1f)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "AI Core",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AI Engine Verification",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hush uses Google's on-device Gemini Nano model. We have verified your system is ready to process commands locally.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.testTag("onboarding_start_button")
        ) {
            Text("Enter Hush")
        }
    }
}

