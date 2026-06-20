# Fix Proposal: Milestone 1 Verification Remediation — Remediation Iteration 3

This document contains step-by-step remediation instructions to solve all integrity violations, dynamic theme facades, prop-drilling issues, and test thread-safety bugs.

---

## 1. Clean Espresso Intents Dependency & Deletion of Fake Stubs

### 1.1 App-Level Gradle Config (`app/build.gradle.kts`)
Add the official Espresso Intents dependency to `app/build.gradle.kts` inside the `dependencies` block:
```kotlin
// Espresso Intents support
androidTestImplementation(libs.androidx.espresso.intents)
```

### 1.2 Delete Fake Local Stubs
Delete the fake stub classes that shadow the official namespace:
- Remove file: `app/src/androidTest/java/androidx/test/espresso/intent/Intents.kt`
- Remove file: `app/src/androidTest/java/androidx/test/espresso/intent/matcher/IntentMatchers.kt`

---

## 2. Enable Room Database Schema Exporting

### 2.1 Update Database Declaration (`app/src/main/java/com/hush/app/data/db/HushDatabase.kt`)
Update the `@Database` annotation to enable schema exporting:
```kotlin
@Database(
    entities = [RuleEntity::class, NotificationLogEntity::class],
    version = 1,
    exportSchema = true
)
```

### 2.2 Create Directory
Ensure the directory `app/schemas` is created to contain Room schema files.

---

## 3. Abstract Permission Checks to Decouple Composable Screens

### 3.1 Create PermissionManager Interface (`app/src/main/java/com/hush/app/domain/permission/PermissionManager.kt`)
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

### 3.2 Implement PermissionManagerImpl (`app/src/main/java/com/hush/app/data/repository/PermissionManagerImpl.kt`)
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
        // No-op
    }
}
```

### 3.3 Create Production Injection Module (`app/src/main/java/com/hush/app/di/PermissionModule.kt`)
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

### 3.4 Create FakePermissionManager for Tests (`app/src/androidTest/java/com/hush/app/mock/FakePermissionManager.kt`)
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

### 3.5 Create Test Module (`app/src/androidTest/java/com/hush/app/di/TestPermissionModule.kt`)
Ensure it replaces the production module:
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

---

## 4. ViewModels to Resolve Prop-Drilling and Handle State

### 4.1 OnboardingViewModel (`app/src/main/java/com/hush/app/ui/screens/onboarding/OnboardingViewModel.kt`)
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

### 4.2 ChatViewModel (`app/src/main/java/com/hush/app/ui/screens/chat/ChatViewModel.kt`)
Exposes all injected services directly:
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

### 4.3 MainViewModel (`app/src/main/java/com/hush/app/MainViewModel.kt`)
Observes shared preferences changes dynamically:
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

---

## 5. UI Layers Refactoring (MainActivity & Screens)

### 5.1 MainActivity (`app/src/main/java/com/hush/app/MainActivity.kt`)
Refactor to remove direct repositories fields, utilize `MainViewModel`, and handle dynamic theme parameters:
```kotlin
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
                else -> isSystemInDarkTheme()
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

### 5.2 Navigation & Screens signatures
Update the signatures of `HushNavigation`, `MainScreen`, `OnboardingScreen`, and `ChatScreen` to:
- Take ViewModels directly using Hilt navigation: `viewModel = hiltViewModel()` inside the destinations.
- Eliminate repository parameter drilling.
- Remove hardcoded permission states inside `OnboardingScreen` and use `DisposableEffect` to trigger `viewModel.refreshPermissions()` on Lifecycle `ON_RESUME`.

---

## 6. Time Range Evaluation & Matching-Only Logging

### 6.1 EvaluateNotificationUseCase (`app/src/main/java/com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt`)
1. Import `java.time.LocalTime`.
2. Add default `currentTime: LocalTime = LocalTime.now()` to the `execute()` parameter list to support time mocking in tests.
3. Compare time windows correctly (both normal daytime ranges and overnight ranges crossing midnight):
   ```kotlin
   if (rule.timeStart != null && rule.timeEnd != null) {
       val inWindow = if (rule.timeStart.isAfter(rule.timeEnd)) {
           // overnight range e.g. 22:00 to 07:00
           !currentTime.isBefore(rule.timeStart) || !currentTime.isAfter(rule.timeEnd)
       } else {
           // normal range e.g. 09:00 to 17:00
           !currentTime.isBefore(rule.timeStart) && !currentTime.isAfter(rule.timeEnd)
       }
       if (!inWindow) continue
   }
   ```
4. Update the logging policy: **Only call `historyRepository.insertLog(...)` when `matchedRuleId != null`** to avoid sqlite storage bloating.

---

## 7. Remove Test Mock Shortcuts & Fix Assertions

### 7.1 RealWorldScenarioE2ETest (`app/src/androidTest/java/com/hush/app/e2e/RealWorldScenarioE2ETest.kt`)
1. Inject `EvaluateNotificationUseCase`.
2. In `simulateNotificationPost`, delete the local matching database queries block and delegate directly to:
   ```kotlin
   evaluateNotificationUseCase.execute(
       packageName = packageName,
       appName = appName,
       title = title,
       text = text,
       sender = sender,
       currentTime = currentTime
   ) == RuleAction.BLOCK
   ```
3. Update the assertions: Since mom's notifications do not match rules, verify that it is allowed and NOT logged:
   ```kotlin
   val momLog = logs.firstOrNull { it.sender == "Mom" }
   assertNull(momLog)
   ```

### 7.2 NotificationInterceptionE2ETest (`app/src/androidTest/java/com/hush/app/e2e/NotificationInterceptionE2ETest.kt`)
1. Assert `0` logs in `testInterception_NoMatchingRules_AllowsNotificationWithoutLogs` and `testInterception_RuleDisabled_BypassesInterception`.
2. Refactor `testInterception_RapidConcurrentNotifications_ThreadSafety` to verify real concurrency:
   ```kotlin
   // Execute 30 concurrent simulation posts using async on Dispatchers.Default
   val jobs = List(30) { i ->
       async(Dispatchers.Default) {
           simulateNotificationPost("com.slack", "Slack", "Title $i", "Content $i", null)
       }
   }
   jobs.awaitAll()
   
   val logs = logDao.getAllLogsFlow().first()
   assertEquals(30, logs.size)
   ```
   (Remove the arbitrary `delay(1000)` call).
