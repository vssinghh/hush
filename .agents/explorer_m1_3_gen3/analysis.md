# Forensic Review and Architectural Recommendations Analysis

This analysis addresses three architectural issues identified in the Gen 2 review:
1. **Hardcoded Mock Permission Bypass in Onboarding screen**
2. **Dynamic Theme Settings Facade**
3. **Prop-Drilling in Compose Navigation Shell**

---

## 1. Hardcoded Mock Permission Bypass in Onboarding Screen

### Problem Analysis
In `OnboardingScreen.kt` (lines 40-43, 53-58), the screen contains local mutable states meant for mocking:
```kotlin
// Lines 40-43
var notificationGrantedMock by remember { mutableStateOf(false) }
var micGrantedMock by remember { mutableStateOf(false) }
var batteryExemptMock by remember { mutableStateOf(false) }
var notificationDeniedMock by remember { mutableStateOf(false) }

// Lines 53-58
fun refreshPermissions() {
    hasNotificationAccess = notificationGrantedMock || isNotificationServiceEnabled(context)
    hasMicrophonePermission = micGrantedMock || ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    isBatteryExempt = batteryExemptMock || isIgnoringBatteryOptimizations(context)
}
```
This hardcoded bypass allows simulated success in tests but leaks mock state logic into the production composable UI. The actual permission status is short-circuited if the mock flags are toggled, compromising runtime security and safety.

### Proposed Architecture Strategy
We decouple permission checks from Compose UI by creating a `PermissionManager` interface. Using Hilt, we provide:
1. A **real production implementation** (`PermissionManagerImpl`) that queries actual system APIs.
2. A **test/fake implementation** (`FakePermissionManager`) inside `androidTest` to control states programmatically during testing.

#### 1.1 Interface Definition (`com.hush.app.domain.permission.PermissionManager`)
```kotlin
package com.hush.app.domain.permission

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult

interface PermissionManager {
    fun hasNotificationAccess(): Boolean
    fun hasMicrophonePermission(): Boolean
    fun isBatteryExempt(): Boolean
    fun isNotificationAccessDenied(): Boolean

    fun requestNotificationAccess(context: Context)
    fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>)
    fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>)
    
    fun setNotificationAccessDenied(denied: Boolean)
}
```

#### 1.2 Production Implementation (`com.hush.app.data.repository.PermissionManagerImpl`)
```kotlin
package com.hush.app.data.repository

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.core.content.ContextCompat
import com.hush.app.domain.permission.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionManager {

    override fun hasNotificationAccess(): Boolean {
        val cn = ComponentName(context, "com.hush.app.service.HushNotificationListener")
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    override fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isBatteryExempt(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    override fun isNotificationAccessDenied(): Boolean = false

    override fun requestNotificationAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        launcher.launch(intent)
    }

    override fun setNotificationAccessDenied(denied: Boolean) {
        // No-op in production
    }
}
```

#### 1.3 Production Dependency Injection Binding (`com.hush.app.di.PermissionModule`)
```kotlin
package com.hush.app.di

import com.hush.app.data.repository.PermissionManagerImpl
import com.hush.app.domain.permission.PermissionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {

    @Binds
    @Singleton
    abstract fun bindPermissionManager(
        permissionManagerImpl: PermissionManagerImpl
    ): PermissionManager
}
```

#### 1.4 Test Mock Double (`com.hush.app.mock.FakePermissionManager` in `androidTest`)
```kotlin
package com.hush.app.mock

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import com.hush.app.domain.permission.PermissionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePermissionManager @Inject constructor() : PermissionManager {
    var notificationGranted = false
    var microphoneGranted = false
    var batteryExempt = false
    var notificationDenied = false

    override fun hasNotificationAccess(): Boolean = notificationGranted
    override fun hasMicrophonePermission(): Boolean = microphoneGranted
    override fun isBatteryExempt(): Boolean = batteryExempt
    override fun isNotificationAccessDenied(): Boolean = notificationDenied

    override fun requestNotificationAccess(context: Context) {
        notificationGranted = true
    }

    override fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        microphoneGranted = true
    }

    override fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        batteryExempt = true
    }

    override fun setNotificationAccessDenied(denied: Boolean) {
        notificationDenied = denied
    }
}
```

#### 1.5 Test Dependency Injection Binding (`com.hush.app.di.TestPermissionModule` in `androidTest`)
```kotlin
package com.hush.app.di

import com.hush.app.domain.permission.PermissionManager
import com.hush.app.mock.FakePermissionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PermissionModule::class]
)
interface TestPermissionModule {

    @Binds
    @Singleton
    fun bindPermissionManager(fake: FakePermissionManager): PermissionManager
}
```

#### 1.6 Onboarding ViewModel (`com.hush.app.ui.screens.onboarding.OnboardingViewModel`)
```kotlin
package com.hush.app.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.hush.app.domain.permission.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissionManager: PermissionManager
) : ViewModel() {

    var hasNotificationAccess by mutableStateOf(false)
        private set

    var hasMicrophonePermission by mutableStateOf(false)
        private set

    var isBatteryExempt by mutableStateOf(false)
        private set

    var isNotificationAccessDenied by mutableStateOf(false)
        private set

    fun refreshPermissions() {
        hasNotificationAccess = permissionManager.hasNotificationAccess()
        hasMicrophonePermission = permissionManager.hasMicrophonePermission()
        isBatteryExempt = permissionManager.isBatteryExempt()
        isNotificationAccessDenied = permissionManager.isNotificationAccessDenied()
    }

    fun requestNotificationAccess(context: Context) {
        permissionManager.requestNotificationAccess(context)
        refreshPermissions()
    }

    fun requestMicrophonePermission(launcher: ManagedActivityResultLauncher<String, Boolean>) {
        permissionManager.requestMicrophonePermission(launcher)
        refreshPermissions()
    }

    fun requestBatteryExemption(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
        permissionManager.requestBatteryExemption(launcher)
        refreshPermissions()
    }

    fun denyNotificationAccess() {
        permissionManager.setNotificationAccessDenied(true)
        refreshPermissions()
    }
}
```

#### 1.7 Refactored `OnboardingScreen.kt` structure
```kotlin
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(0) }
    var showBatteryWarning by remember { mutableStateOf(false) }

    // Refresh permissions whenever ON_RESUME triggers
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
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

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.refreshPermissions()
        } else {
            showBatteryWarning = true
        }
    }

    // ... inside Scaffolding step navigation ...
    PermissionsStep(
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
    // ...
}
```

---

## 2. Dynamic Theme Settings Facade

### Problem Analysis
`SettingsScreen.kt` updates the theme preference value `"theme_option"` in `hush_preferences` SharedPreferences, but `MainActivity.kt` completely ignores this preference, setting `HushTheme` without arguments. Consequently, modifying the appearance mode inside Settings does not dynamically update the visual hierarchy.

### Proposed Architecture Strategy
Expose the theme state through a central `MainViewModel` backed by a reactive `SharedPreferences.OnSharedPreferenceChangeListener`. The view model collects SharedPreferences updates as a `StateFlow` and pushes changes to the compose UI tree inside `MainActivity`.

#### 2.1 Refactored `MainViewModel.kt`
```kotlin
package com.hush.app

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.hush.app.data.pref.OnboardingPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val onboardingPrefs: OnboardingPrefs,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE)

    private val _themeOption = MutableStateFlow(
        prefs.getString("theme_option", "System Default") ?: "System Default"
    )
    val themeOption: StateFlow<String> = _themeOption.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme_option") {
            _themeOption.value = prefs.getString("theme_option", "System Default") ?: "System Default"
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    val isOnboardingCompleted: Boolean
        get() = onboardingPrefs.isOnboardingCompleted

    fun setOnboardingCompleted(completed: Boolean) {
        onboardingPrefs.isOnboardingCompleted = completed
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}
```

#### 2.2 Refactored `MainActivity.kt`
```kotlin
package com.hush.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.hush.app.ui.navigation.HushNavigation
import com.hush.app.ui.theme.HushTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeOption by mainViewModel.themeOption.collectAsState()
            val darkTheme = when (themeOption) {
                "Dark Theme" -> true
                "Light Theme" -> false
                else -> isSystemInDarkTheme() // "System Default"
            }

            HushTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    HushNavigation(
                        navController = navController,
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }
}
```

---

## 3. Prop-Drilling in Compose Navigation Shell

### Problem Analysis
`MainActivity.kt` injects repository interfaces (`AIEngine`, `SpeechRecognizerWrapper`, `RuleRepository`, `OnboardingPrefs`) and drills them through parameters down to nested Composables:
`MainActivity` -> `HushNavigation` -> `MainScreen` -> `ChatScreen`.
This makes components difficult to reuse, destroys layout previewability, and creates unnecessary coupling.

### Proposed Architecture Strategy
Remove direct dependency injections from `MainActivity`. Instead, use `hiltViewModel()` within composable targets (`OnboardingScreen`, `ChatScreen`, etc.) to obtain the ViewModels containing these repositories. 

#### 3.1 Refactored `HushNavigation.kt`
```kotlin
package com.hush.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hush.app.MainViewModel
import com.hush.app.ui.screens.MainScreen
import com.hush.app.ui.screens.onboarding.OnboardingScreen

@Composable
fun HushNavigation(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val startDestination = if (mainViewModel.isOnboardingCompleted) {
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
                    mainViewModel.setOnboardingCompleted(true)
                    navController.navigate(ScreenRoute.Main.route) {
                        popUpTo(ScreenRoute.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(ScreenRoute.Main.route) {
            MainScreen(
                onResetOnboarding = {
                    mainViewModel.setOnboardingCompleted(false)
                    navController.navigate(ScreenRoute.Onboarding.route) {
                        popUpTo(ScreenRoute.Main.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
```

#### 3.2 Refactored `MainScreen.kt`
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
import androidx.compose.ui.platform.testTag
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
                                popUpTo(childNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.testTag("bottom_nav_${tab.route}")
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

#### 3.3 New `ChatViewModel.kt` (`com.hush.app.ui.screens.chat.ChatViewModel`)
```kotlin
package com.hush.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.RuleRepository
import com.hush.app.domain.repository.SpeechRecognizerWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    val aiEngine: AIEngine,
    val speechRecognizerWrapper: SpeechRecognizerWrapper,
    val ruleRepository: RuleRepository
) : ViewModel()
```

#### 3.4 Refactored `ChatScreen.kt` Destination
```kotlin
package com.hush.app.ui.screens.chat

import androidx.hilt.navigation.compose.hiltViewModel
// ... other imports

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val aiEngine = viewModel.aiEngine
    val speechRecognizerWrapper = viewModel.speechRecognizerWrapper
    val ruleRepository = viewModel.ruleRepository
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ... all remaining code in ChatScreen stays intact ...
}
```

---

## 4. Verification and Compliance

- **Clean Architecture & Separation of Concerns**: We moved permission checking and handling logic out of Composable files. Composables now delegate actions to the VM, which interacts with the abstract interface.
- **Hilt Testing Compatibility**: The Hilt configuration relies on a standard interface mapping. Instrumented tests swap implementations in the test classpath without modifying production compose code.
- **Dynamic Recompositions**: By listening to changes on SharedPreferences and mapping them to a `StateFlow`, Compose will instantly update the theme colors of the app mid-session when the preference updates.
