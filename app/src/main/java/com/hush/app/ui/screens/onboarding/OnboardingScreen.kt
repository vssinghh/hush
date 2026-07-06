package com.hush.app.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hush.app.ui.components.DuskGradient
import com.hush.app.ui.theme.HushSerif
import com.hush.app.ui.theme.SageGreen

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

    // Warning Dialog for Battery Optimization Denial
    if (showBatteryWarning) {
        AlertDialog(
            onDismissRequest = { showBatteryWarning = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text("Keep App Alive") },
            text = { Text("Hush works best when exempted from battery restrictions.") },
            confirmButton = {
                Button(
                    onClick = { showBatteryWarning = false },
                    shape = CircleShape,
                    modifier = Modifier.testTag("onboarding_battery_warning_dismiss")
                ) {
                    Text("Dismiss")
                }
            },
            modifier = Modifier.testTag("onboarding_battery_warning")
        )
    }

    Scaffold(
        modifier = modifier.testTag("onboarding_screen"),
        containerColor = MaterialTheme.colorScheme.background
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
                                viewModel.requestBatteryExemption(context)
                                // If the exemption still isn't granted, gently
                                // explain why it helps (battery is optional).
                                if (!viewModel.isBatteryExempt) showBatteryWarning = true
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

            // Step Indicator — animated pill for the active step
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 20.dp)
            ) {
                repeat(3) { index ->
                    val isActive = index == currentStep
                    val width by animateDpAsState(
                        targetValue = if (isActive) 28.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "dot_width_$index"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ColumnScope.WelcomeStep(onNext: () -> Unit) {
    // Springy hero entrance
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val heroScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "hero_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.weight(1f)
    ) {
        // Dusk-sky hero — the one gradient moment in the whole app
        Box(
            modifier = Modifier
                .size(104.dp)
                .graphicsLayer {
                    scaleX = heroScale
                    scaleY = heroScale
                }
                .clip(CircleShape)
                .background(DuskGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Hush",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Control notifications by simply\ntalking to your phone",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = HushSerif,
                fontStyle = FontStyle.Italic
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(36.dp))

        FeatureRow(
            icon = Icons.Outlined.AutoAwesome,
            title = "Natural language rules",
            description = "\"Mute Slack after 10pm\" — done."
        )
        Spacer(modifier = Modifier.height(14.dp))
        FeatureRow(
            icon = Icons.Outlined.Lock,
            title = "100% private",
            description = "Gemini Nano runs on-device. Nothing leaves your phone."
        )
        Spacer(modifier = Modifier.height(14.dp))
        FeatureRow(
            icon = Icons.Outlined.Schedule,
            title = "Time-aware filtering",
            description = "Rules that only fire when you need quiet."
        )

        Spacer(modifier = Modifier.height(40.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("onboarding_next_button"),
            shape = CircleShape
        ) {
            Text("Get Started", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onNext: () -> Unit,
    canProceed: Boolean,
    showDenyRationale: Boolean
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = "A few permissions",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Hush needs these to quiet things down.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        // 1. Notification Access
        PermissionRow(
            icon = Icons.Filled.Notifications,
            title = "Notification access",
            description = "Read and filter incoming notifications. Required.",
            isGranted = hasNotificationAccess,
            onRequest = onRequestNotification,
            buttonTag = "onboarding_grant_notification"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Microphone
        PermissionRow(
            icon = Icons.Filled.Mic,
            title = "Microphone",
            description = "Speak your rules out loud. Optional.",
            isGranted = hasMicrophonePermission,
            onRequest = onRequestMicrophone,
            buttonTag = "onboarding_grant_mic"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Battery Exclusion
        PermissionRow(
            icon = Icons.Outlined.BatteryChargingFull,
            title = "Keep app alive",
            description = "Skip battery limits so filtering never sleeps. Optional.",
            isGranted = isBatteryExempt,
            onRequest = onRequestBattery,
            buttonTag = "onboarding_ignore_battery"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNext,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("onboarding_next_button"),
            shape = CircleShape
        ) {
            Text("Continue", style = MaterialTheme.typography.labelLarge)
        }

        AnimatedVisibility(
            visible = showDenyRationale,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500))
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
    buttonTag: String? = null,
    icon: ImageVector = Icons.Filled.Notifications
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isGranted) SageGreen.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (isGranted) SageGreen.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) SageGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = SageGreen
                )
            } else {
                Button(
                    onClick = onRequest,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
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
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(SageGreen.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Ready",
                tint = SageGreen,
                modifier = Modifier.size(56.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "You're all set",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hush parses your commands right on this device — with Gemini Nano when available, and a built-in parser everywhere else. No cloud, ever.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("onboarding_start_button"),
            shape = CircleShape
        ) {
            Text("Enter Hush", style = MaterialTheme.typography.labelLarge)
        }
    }
}
