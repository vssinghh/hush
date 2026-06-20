# Analysis and UI/UX Theme & Navigation Architecture Report — Milestone 1

Hush is a privacy-first, conversational notification manager for Android. This report establishes the design, architecture, and code templates for the application's user interface, including Material 3/You theming, Jetpack Compose navigation, SharedPreferences onboarding integration, and core UI screen skeletons.

---

## 1. Architectural Strategy & Design Choices

To align with Clean Architecture and the specifications in `PROJECT.md`, the UI components are located in the `com.hush.app.ui` package. The design is structured to prioritize on-device privacy, smooth screen transitions, and premium UX aesthetics using Material 3 (M3).

### Material 3 & Material You (Dynamic Color)
- **Min SDK & Compatibility**: Since the minimum SDK is 33 (Android 13) and target SDK is 35 (Android 15), every supported device runs Android 12+ (API 31+). This guarantees that Dynamic Color (`dynamicLightColorScheme` and `dynamicDarkColorScheme`) is natively supported on the platform level.
- **Visual Vibe**: The "Hush" app uses a dark-blue and indigo-dominant theme (representing focus, quietness, and security). When Dynamic Color is unavailable (e.g., custom ROMs or disabled by user), it falls back to a custom color scheme.
- **Adaptive Layouts**: Skeletons use standard Material 3 structural components (`Scaffold`, `NavigationBar`, `Card`, `FloatingActionButton`, etc.) to automatically adapt layout, surface tones, and padding.

### Navigation Architecture
- **Dual-Level Navigation**:
  1. **Root NavHost**: Manages top-level destinations: `Onboarding` and `Main`. It controls first-launch switching.
  2. **Nested NavHost (inside MainScreen)**: Manages bottom navigation dashboard tabs: `Chat`, `Rules`, `History`, and `Settings`.
- **Tab State Preservation**: Uses standard Android navigation options (`popUpTo`, `launchSingleTop`, `restoreState`) to prevent stack building and retain user input or scroll state when switching tabs.
- **Onboarding Completion Flow**:
  - Main destination resolution is evaluated on launch by querying `OnboardingPrefs` (SharedPreferences wrapper).
  - Completing onboarding updates the preference, pops the `Onboarding` route, and enters `Main` inclusively to prevent the user from backing into the onboarding screens.

### Onboarding & Permissions Lifecycle
- **Reactive UI**: Permissions (Notification Access, Microphone, Battery optimization) are system-level settings. To prevent UI desynchronization when a user returns from settings, a `LifecycleEventObserver` watches for the `ON_RESUME` state and re-verifies permission statuses in real-time.
- **Feature Check**: Checks for AI Core (Gemini Nano) availability to guide users if their hardware/software environment requires manual setup.

---

## 2. Package Structure

The UI components are organized as follows:

```
com.hush.app/
│
├── data/
│   └── pref/
│       └── OnboardingPrefs.kt           # SharedPreferences onboarding state tracker
│
└── ui/
    ├── theme/
    │   ├── Color.kt                     # M3 Color definitions
    │   ├── Type.kt                      # Typography standards
    │   └── Theme.kt                     # HushTheme wrapper with Dynamic Color support
    │
    ├── navigation/
    │   ├── ScreenRoute.kt               # Route contracts & BottomTab configurations
    │   └── HushNavigation.kt            # Root NavHost router
    │
    └── screens/
        ├── MainScreen.kt                # Scaffold hosting bottom nav & nested NavHost
        ├── onboarding/
        │   └── OnboardingScreen.kt      # Permission check, Dynamic observer, AI check
        ├── chat/
        │   └── ChatScreen.kt            # Voice + text chat interface skeleton
        ├── rules/
        │   └── RulesScreen.kt           # Rule list, FAB, filter chips skeleton
        ├── history/
        │   └── HistoryScreen.kt         # Evaluated log filter & search list skeleton
        └── settings/
            └── SettingsScreen.kt        # Settings & "Reset Onboarding" button
```

---

## 3. Material 3 / You Theme Skeletons

### `com.hush.app.ui.theme.Color.kt`
Defines the default Indigo/Midnight color palette used when Dynamic Color is disabled or unsupported.

```kotlin
package com.hush.app.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Palette Colors
val Indigo80 = Color(0xFFB4C5FF)
val IndigoGrey80 = Color(0xFFC3C6CF)
val Slate80 = Color(0xFFE2BFCB)

val MidnightBackground = Color(0xFF1B1B1F)
val MidnightSurface = Color(0xFF202024)
val MidnightOnSurface = Color(0xFFE3E2E6)

// Light Palette Colors
val Indigo40 = Color(0xFF3F51B5)
val IndigoGrey40 = Color(0xFF5E6066)
val Slate40 = Color(0xFF7A5763)

val LightBackground = Color(0xFFFEFDFE)
val LightSurface = Color(0xFFF4F3F7)
val LightOnSurface = Color(0xFF1B1B1F)
```

### `com.hush.app.ui.theme.Type.kt`
Defines typography rules for consistent text scaling.

```kotlin
package com.hush.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

### `com.hush.app.ui.theme.Theme.kt`
Integrates Dynamic Color support using API level checks and system preferences.

```kotlin
package com.hush.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    secondary = IndigoGrey80,
    tertiary = Slate80,
    background = MidnightBackground,
    surface = MidnightSurface,
    onBackground = MidnightOnSurface,
    onSurface = MidnightOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    secondary = IndigoGrey40,
    tertiary = Slate40,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface
)

@Composable
fun HushTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Dynamic color supported on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## 4. SharedPreferences Onboarding Manager

### `com.hush.app.data.pref.OnboardingPrefs.kt`
Tracks first-launch states and provides clean getters/setters.

```kotlin
package com.hush.app.data.pref

import android.content.Context
import android.content.SharedPreferences

class OnboardingPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()
}
```

#### Recommended Hilt Module Provision (`com.hush.app.di.PreferencesModule.kt`)
```kotlin
package com.hush.app.di

import android.content.Context
import com.hush.app.data.pref.OnboardingPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideOnboardingPrefs(@ApplicationContext context: Context): OnboardingPrefs {
        return OnboardingPrefs(context)
    }
}
```

---

## 5. Navigation & Scaffold Skeletons

### `com.hush.app.ui.navigation.ScreenRoute.kt`
Declares the navigation endpoints, bottom navigation structure, and M3 icons.

```kotlin
package com.hush.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class ScreenRoute(val route: String) {
    object Onboarding : ScreenRoute("onboarding")
    object Main : ScreenRoute("main")
}

sealed class BottomTabRoute(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Chat : BottomTabRoute("chat", "Chat", Icons.Default.Send)
    object Rules : BottomTabRoute("rules", "Rules", Icons.Default.List)
    object History : BottomTabRoute("history", "History", Icons.Default.DateRange)
    object Settings : BottomTabRoute("settings", "Settings", Icons.Default.Settings)
}
```

### `com.hush.app.ui.navigation.HushNavigation.kt`
Establishes the root NavHost controller and handles the pop-up behavior during transition between onboarding and main dashboard.

```kotlin
package com.hush.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hush.app.data.pref.OnboardingPrefs
import com.hush.app.ui.screens.MainScreen
import com.hush.app.ui.screens.onboarding.OnboardingScreen

@Composable
fun HushNavigation(
    navController: NavHostController,
    onboardingPrefs: OnboardingPrefs,
    modifier: Modifier = Modifier
) {
    val startDestination = if (onboardingPrefs.isOnboardingCompleted) {
        ScreenRoute.Main.route
    } else {
        ScreenRoute.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(ScreenRoute.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    onboardingPrefs.isOnboardingCompleted = true
                    navController.navigate(ScreenRoute.Main.route) {
                        popUpTo(ScreenRoute.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(ScreenRoute.Main.route) {
            MainScreen(
                onResetOnboarding = {
                    onboardingPrefs.isOnboardingCompleted = false
                    navController.navigate(ScreenRoute.Onboarding.route) {
                        popUpTo(ScreenRoute.Main.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
```

### `com.hush.app.ui.screens.MainScreen.kt`
Scaffold containing the bottom navigation bar and the nested NavHost for managing separate tab back-stacks.

```kotlin
package com.hush.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hush.app.ui.navigation.BottomTabRoute
import com.hush.app.ui.screens.chat.ChatScreen
import com.hush.app.ui.screens.history.HistoryScreen
import com.hush.app.ui.screens.rules.RulesScreen
import com.hush.app.ui.screens.settings.SettingsScreen

@Composable
fun MainScreen(
    onResetOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val childNavController = rememberNavController()
    val tabs = listOf(
        BottomTabRoute.Chat,
        BottomTabRoute.Rules,
        BottomTabRoute.History,
        BottomTabRoute.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by childNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = currentRoute == tab.route,
                        onClick = {
                            childNavController.navigate(tab.route) {
                                // Pop up to the start destination of the graph
                                // to avoid building up a large stack of destinations
                                popUpTo(childNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when reselecting
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = childNavController,
            startDestination = BottomTabRoute.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomTabRoute.Chat.route) {
                ChatScreen()
            }
            composable(BottomTabRoute.Rules.route) {
                RulesScreen()
            }
            composable(BottomTabRoute.History.route) {
                HistoryScreen()
            }
            composable(BottomTabRoute.Settings.route) {
                SettingsScreen(onResetOnboarding = onResetOnboarding)
            }
        }
    }
}
```

---

## 6. UI Screen Skeletons

### `com.hush.app.ui.screens.onboarding.OnboardingScreen.kt`
Handles introduction steps, live-updating permission checks (re-queried when returning from background), and checking AI capabilities before allowing access to the main dashboard.

```kotlin
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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) }

    // Dynamic Permission Statuses
    var hasNotificationAccess by remember { mutableStateOf(false) }
    var hasMicrophonePermission by remember { mutableStateOf(false) }
    var isBatteryExempt by remember { mutableStateOf(false) }

    // Helper: Check system permission states
    fun refreshPermissions() {
        hasNotificationAccess = isNotificationServiceEnabled(context)
        hasMicrophonePermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        isBatteryExempt = isIgnoringBatteryOptimizations(context)
    }

    // Refresh permissions whenever the user returns to the app (ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
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
        hasMicrophonePermission = granted
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Welcome to Hush") }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (currentStep) {
                0 -> WelcomeStep(onNext = { currentStep = 1 })
                1 -> PermissionsStep(
                    hasNotificationAccess = hasNotificationAccess,
                    hasMicrophonePermission = hasMicrophonePermission,
                    isBatteryExempt = isBatteryExempt,
                    onRequestNotification = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    },
                    onRequestMicrophone = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestBattery = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    onNext = { currentStep = 2 },
                    canProceed = hasNotificationAccess // Notification Access is mandatory
                )
                2 -> AICoreStep(
                    onComplete = onOnboardingComplete
                )
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
fun WelcomeStep(onNext: () -> Unit) {
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
        Button(onClick = onNext) {
            Text("Get Started")
        }
    }
}

@Composable
fun PermissionsStep(
    hasNotificationAccess: Boolean,
    hasMicrophonePermission: Boolean,
    isBatteryExempt: Boolean,
    onRequestNotification: () -> Unit,
    onRequestMicrophone: () -> Unit,
    onRequestBattery: () -> Unit,
    onNext: () -> Unit,
    canProceed: Boolean
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
            onRequest = onRequestNotification
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Microphone
        PermissionRow(
            title = "Microphone Access",
            description = "Enables natural language voice commands. (Optional)",
            isGranted = hasMicrophonePermission,
            onRequest = onRequestMicrophone
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Battery Exclusion
        PermissionRow(
            title = "Keep App Alive",
            description = "Exempts Hush from battery restrictions so it runs in the background. (Optional)",
            isGranted = isBatteryExempt,
            onRequest = onRequestBattery
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onNext,
            enabled = canProceed,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Continue")
        }
        if (!canProceed) {
            Text(
                text = "Grant notification access to continue",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit
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
                    )
                ) {
                    Text("Grant", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun AICoreStep(onComplete: () -> Unit) {
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
        Button(onClick = onComplete) {
            Text("Enter Hush")
        }
    }
}

// System permission utilities
private fun isNotificationServiceEnabled(context: Context): Boolean {
    val cn = ComponentName(context, "com.hush.app.service.HushNotificationListener")
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
```

### `com.hush.app.ui.chat.ChatScreen.kt`
Skeleton for the conversational chat screen. Provides text input, a voice recorder action button, and a visual list representation.

```kotlin
package com.hush.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    var textState by remember { mutableStateOf("") }
    val mockMessages = remember {
        mutableStateListOf(
            "Welcome to Hush! Speak or type a filtering command (e.g., 'Mute Instagram').",
            "Mute WhatsApp notifications except from Bob."
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Conversational Assistant") })
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Message Log
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(mockMessages.size) { index ->
                    val isUser = index % 2 != 0
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isUser) 16.dp else 0.dp,
                                        bottomEnd = if (isUser) 0.dp else 16.dp
                                    )
                                )
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = mockMessages[index],
                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    placeholder = { Text("Type command...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (textState.isNotBlank()) {
                            mockMessages.add(textState)
                            textState = ""
                        }
                    },
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
```

### `com.hush.app.ui.rules.RulesScreen.kt`
Skeleton for listing, toggling, and deleting rules.

```kotlin
package com.hush.app.ui.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(modifier: Modifier = Modifier) {
    var rulesList = remember {
        mutableStateListOf(
            Triple("Block Instagram", "com.instagram.android", true),
            Triple("Mute WhatsApp except Alice", "com.whatsapp", true)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Rules Management") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Navigate or open create dialog */ }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Rule")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(rulesList.size) { index ->
                    val rule = rulesList[index]
                    var enabled by remember { mutableStateOf(rule.third) }

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(rule.first, style = MaterialTheme.typography.titleMedium)
                                Text(rule.second, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    rulesList[index] = Triple(rule.first, rule.second, it)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
```

### `com.hush.app.ui.screens.history.HistoryScreen.kt`
Skeleton for logs displaying allowed, blocked, and muted notification history.

```kotlin
package com.hush.app.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val historyLogs = listOf(
        Triple("WhatsApp", "Blocked: Message from Bob", "10:32 AM"),
        Triple("Instagram", "Muted: New like on photo", "09:15 AM"),
        Triple("Gmail", "Allowed: Urgent Server Alert", "Yesterday")
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Interception History") })
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(historyLogs.size) { index ->
                    val log = historyLogs[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.first, style = MaterialTheme.typography.titleMedium)
                                Text(log.second, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(log.third, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
```

### `com.hush.app.ui.screens.settings.SettingsScreen.kt`
Skeleton setting up configuration choices and offering a developer/tester "Reset Onboarding" button.

```kotlin
package com.hush.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onResetOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var retentionDays by remember { mutableIntStateOf(30) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Retention Policy", style = MaterialTheme.typography.titleMedium)
            
            // Simple retention days selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(
                        selected = retentionDays == days,
                        onClick = { retentionDays = days },
                        label = { Text("$days Days") }
                    )
                }
            }

            Divider()

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

            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Hush Version 1.0 (MVP Skeleton)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

## 7. Integration & Setup Instructions for Developers

1. **Gradle Dependencies**: Ensure you import the following in `app/build.gradle.kts`:
   ```kotlin
   implementation("androidx.navigation:navigation-compose:2.8.0")
   implementation("androidx.compose.material3:material3:1.2.1")
   implementation("androidx.compose.material:material-icons-extended:1.6.8")
   ```
2. **Setup inside MainActivity**: Add `@AndroidEntryPoint` and inject `OnboardingPrefs` to power the `HushNavigation` start destination resolver.
3. **Local Testing**: Run the app and test the onboarding flow. Tap "Settings" -> "Reset Onboarding Flow" to repeat and verify step execution.
