# Milestone 6 (Onboarding & Polish) Analysis & Strategy Report

## Executive Summary
This report analyzes the current state of Milestone 6 (Onboarding & Polish) for the Hush application. 
During our investigation, we compiled and ran the existing 54 instrumented E2E tests, and all of them passed successfully on the local emulator. However, the production implementation has several structural gaps relative to real-world usage and UX quality requirements:
1. **Onboarding Flow**: The actual production `PermissionManagerImpl` lacks real implementation for tracking when the user has denied notification permissions, returning constant dummy values (`false`).
2. **Settings Preferences**: The "Light Theme" option is not exposed in the settings screen UI, although `MainActivity` is fully prepared to handle it.
3. **Database Retention**: Pruning of old logs only occurs manually when the retention preference is changed. There is no automatic trigger on app startup or log insertion.
4. **UI/UX Polish**: Standard navigation screen-to-screen transitions are currently default and lacks animations. Button ripples are poorly bounded on background-decorated icon buttons, and the unsupported AI warning banner appears instantly without animation.

We provide concrete design recommendations and code proposals to fix these gaps.

---

## 1. Onboarding Flow & Permission Denial Rationale

### Direct Observations & Gaps
In `AppFoundationE2ETest.kt`, the test `testOnboarding_DenyNotificationAccess_ShowsRationaleAndDisablesNext` clicks the hidden mock button `onboarding_grant_notification_deny_mock` to simulate denial. This triggers `viewModel.denyNotificationAccess()`, which calls `permissionManager.setNotificationAccessDenied(true)`.

In the real application wrapper `PermissionManagerImpl.kt` (lines 41, 61-63):
```kotlin
    override fun isNotificationAccessDenied(): Boolean = false
    
    override fun setNotificationAccessDenied(denied: Boolean) {
        // No-op
    }
```
As a result:
- The real app never tracks when the user has denied notification access.
- The warning rationale (`onboarding_deny_rationale`) is never shown to the user in a production build, and the user can proceed without seeing it.

### Proposed Fix
We should modify `PermissionManagerImpl.kt` to save and read the `notification_access_denied` flag to/from SharedPreferences. This will align the production behavior with the mock and make the warning rationale work in production.

#### Proposed Changes in `PermissionManagerImpl.kt`:
```kotlin
private const val PREF_KEY_NOTIFICATION_DENIED = "notification_access_denied"

@Singleton
class PermissionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PermissionManager {

    private val prefs by lazy {
        context.getSharedPreferences("hush_preferences", Context.MODE_PRIVATE)
    }

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

    override fun isNotificationAccessDenied(): Boolean {
        // If they have access, it's not denied anymore
        if (hasNotificationAccess()) {
            setNotificationAccessDenied(false)
            return false
        }
        return prefs.getBoolean(PREF_KEY_NOTIFICATION_DENIED, false)
    }

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
        prefs.edit().putBoolean(PREF_KEY_NOTIFICATION_DENIED, denied).apply()
    }
}
```

---

## 2. Settings Preferences & Theme Options

### Direct Observations & Gaps
In `MainActivity.kt`, the theme mode is determined as follows:
```kotlin
            val darkTheme = when (themeOption) {
                "Dark Theme" -> true
                "Light Theme" -> false
                else -> isSystemInDarkTheme()
            }
```
However, in `SettingsScreen.kt`, the theme selection UI only displays two buttons:
1. `Dark Theme` (`settings_theme_dark_option`)
2. `System Default` (`settings_theme_system_option`)

There is no button or preference state to choose "Light Theme".

### Proposed Fix
Expose "Light Theme" inside the `SettingsScreen` UI.

#### Proposed Changes in `SettingsScreen.kt` (under `if (showThemeMenu)`):
```kotlin
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
```

---

## 3. Settings DB Retention Pruning Logs Deletion

### Direct Observations & Gaps
- In `SettingsScreen.kt`, database pruning is triggered inside the click callbacks of the retention menu buttons (e.g. `pruneDatabase("7 Days")`).
- Pruning does **NOT** run automatically when the app starts, nor does it run when new logs are added to the database. Over time, the database will accumulate logs indefinitely until a user manually navigates to Settings and changes the setting.

### Proposed Fix
We should run the pruning automatically during app initialization or inside the ViewModel so that database size is capped as requested.
We can add database pruning to `MainViewModel.kt` on initialization.

#### Proposed Changes in `MainViewModel.kt`:
1. Inject `HistoryRepository` into `MainViewModel.kt`'s constructor.
2. In `init { }`, read the current `retention_policy` from preferences, calculate the threshold, and launch a coroutine to prune old logs.

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val onboardingPrefs: OnboardingPrefs,
    private val historyRepository: HistoryRepository, // Inject historyRepository
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
        pruneDatabaseOnStartup()
    }

    private fun pruneDatabaseOnStartup() {
        val policy = prefs.getString("retention_policy", "30 Days") ?: "30 Days"
        val days = when (policy) {
            "7 Days" -> 7L
            "30 Days" -> 30L
            "90 Days" -> 90L
            else -> return
        }
        val threshold = java.time.Instant.now().minus(days, java.time.temporal.ChronoUnit.DAYS)
        androidx.lifecycle.viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                historyRepository.deleteLogsOlderThan(threshold)
            } catch (e: Exception) {
                android.util.Log.e("HushPruning", "Automatic startup pruning failed", e)
            }
        }
    }
...
```

---

## 4. UI/UX Polish

### A. Navigation Transitions
We should add animated slide/fade transitions when navigating between the `Onboarding` and `Main` screens, as well as between bottom navigation tabs.

#### Proposed Changes in `HushNavigation.kt` (`NavHost`):
```kotlin
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
        exitTransition = { fadeOut(tween(300)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
        popEnterTransition = { fadeIn(tween(300)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
        popExitTransition = { fadeOut(tween(300)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) }
    )
```

#### Proposed Changes in `MainScreen.kt` (`NavHost`):
```kotlin
        NavHost(
            navController = childNavController,
            startDestination = BottomTabRoute.Chat.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) }
        )
```

### B. Warning Banner Fade-ins
In `ChatScreen.kt` (lines 67-87), the unsupported AI warning banner appears instantly. It should use `AnimatedVisibility` for a smooth fade-in and slide-in effect.

#### Proposed Changes in `ChatScreen.kt`:
```kotlin
            // Unsupported AI Banner with fade/slide animation
            AnimatedVisibility(
                visible = !aiEngine.isAvailable(),
                enter = fadeIn(tween(500)) + slideInVertically(animationSpec = tween(500)),
                exit = fadeOut(tween(500)) + slideOutVertically(animationSpec = tween(500))
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("ai_unsupported_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Error")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Gemini Nano is not supported on this device.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
```

### C. Button Ripples
In `ChatScreen.kt` (lines 273-312), the background modifier for the Send and Mic `IconButton` components is set directly:
```kotlin
                IconButton(
                    onClick = { ... },
                    modifier = Modifier.background(...)
                )
```
This causes the ripple effect (which is a circular bounds ripple in M3) to be drawn incorrectly or behind the custom background shape. We should clip the shape to a circle **before** applying any background or click listeners, or use `FilledIconButton`:
```kotlin
                IconButton(
                    onClick = {
                        if (textState.isNotBlank()) {
                            viewModel.handleSend(textState)
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape) // Ensures the ripple is bounded inside the circle
                        .background(
                            if (aiEngine.isAvailable()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        .testTag("chat_send_button"),
                    enabled = aiEngine.isAvailable()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
```
The same clipping should be applied to the Mic button.

Also in `HistoryScreen.kt` (line 104), standard `Card` clickable modifier:
```kotlin
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLog = log },
                        ...
                    )
```
Instead, we should use the Material 3 `Card` with built-in `onClick` parameter, which handles ripple constraints and card corner masking correctly:
```kotlin
                    Card(
                        onClick = { selectedLog = log },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
```

---

## 5. E2E Test Execution Summary

- **Command**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew connectedAndroidTest`
- **Output Status**: BUILD SUCCESSFUL (0 failed, 54 passed)
- **Time Elapsed**: 58 seconds
- **Verification Environment**: Emulator `emulator-5554` running Android API 15 (mocked system API levels).

All tests are fully operational and verify the correct structure of both onboarding (battery optimization warnings, notification denial mock rationales) and settings screen functionality (retention and theme configuration persistence).
