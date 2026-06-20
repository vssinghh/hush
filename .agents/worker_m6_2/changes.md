# Detailed Description of Changes

## 1. Remove Test Backdoor in Production Code & Implement Real Permission Denial Detection
### `OnboardingScreen.kt`
- Removed the hidden `Box` test-tagged `"onboarding_grant_notification_deny_mock"` (lines 223-228).
- Removed the unused private helper functions `isNotificationServiceEnabled` and `isIgnoringBatteryOptimizations` at the bottom of the file.
- Added a check in the `ON_RESUME` Lifecycle observer block: if `viewModel.isNotificationPermissionRequested` is true and `viewModel.hasNotificationAccess` is false, it calls `viewModel.denyNotificationAccess()`, then `viewModel.refreshPermissions()`.
- Wildcard imported `androidx.compose.animation.*` and wrapped the `when (currentStep)` block inside `AnimatedContent` for sliding transitions between onboarding steps.
- Wrapped the step content inside a `Column` with `fillMaxSize` to provide the expected `ColumnScope` to the child welcome, permission, and AI core step composables.

### `OnboardingViewModel.kt`
- Added the state variable `var isNotificationPermissionRequested by mutableStateOf(false)`.
- Set `isNotificationPermissionRequested = true` in `requestNotificationAccess(context)`.

## 2. Refactor Settings Screen to follow MVVM & Dispatchers.IO
### `SettingsViewModel.kt` (New File)
- Implemented a Hilt-injected ViewModel class.
- Injected `HistoryRepository` and `PermissionManager`.
- Exposed state flows `isNotificationActive` and `isVoiceActive`.
- Implemented `pruneDatabase(policy: String)` running on `Dispatchers.IO` via `viewModelScope.launch(Dispatchers.IO)` with proper `HushPruning` logging.

### `SettingsScreen.kt`
- Refactored to inject `SettingsViewModel` via `hiltViewModel()`.
- Removed the manual Dagger entry points (`SettingsEntryPoint` and `@EntryPoint`).
- Observed the notification and voice states from the viewmodel flow as Compose states.
- Replaced the local `pruneDatabase` method with direct calls to `viewModel.pruneDatabase(policy)`.
- Let the `ON_RESUME` Lifecycle observer call `viewModel.refreshPermissions()`.

## 3. Thread Safety for Startup Database Pruning
### `MainViewModel.kt`
- Extracted startup pruning to a separate private function `pruneDatabaseOnStartup(days: Long)` which launches its coroutine on `Dispatchers.IO` (`viewModelScope.launch(Dispatchers.IO)`).
- Imported `kotlinx.coroutines.Dispatchers`.

## 4. Visual Polish (M3 FilledIconButton & Ripples)
### `ChatScreen.kt`
- Replaced the custom styled `IconButton`s for send and mic actions with standard Material 3 `FilledIconButton` components.
- Removed custom `.clip(CircleShape)` and `.background(...)` modifiers and icon tints from them, allowing M3 `FilledIconButton` to handle the background, ripples, and disabled colors natively.

## 5. Test Stability & Leak Fixes (createEmptyComposeRule)
### E2E Tests: `AppFoundationE2ETest.kt`, `ConversationalAIE2ETest.kt`, `CrossFeatureE2ETest.kt`, `RealWorldScenarioE2ETest.kt`
- Modified the compose rule definition to use `createEmptyComposeRule()` from `androidx.compose.ui.test.junit4.createEmptyComposeRule`.
- Updated test setups to manually launch the activity via `ActivityScenario.launch<MainActivity>(intent)` where required, and ensured the scenarios are closed in `tearDown()`.
- In `AppFoundationE2ETest.kt`, updated `testOnboarding_DenyNotificationAccess_ShowsRationaleAndDisablesNext` to:
  1. Remove the call to click `onboarding_grant_notification_deny_mock`.
  2. Click the real `onboarding_grant_notification` button.
  3. Set the fake permission manager status `notificationGranted = false` and `notificationDenied = true`.
  4. Recreate the activity using `recreateActivityAndWait("onboarding_screen", freshLaunch = false)` which triggers `ON_RESUME`.
  5. Navigate back to the permissions step by clicking `onboarding_next_button` (as activity recreation resets compose-level `currentStep` to 0).
  6. Assert that the rationale is shown and the next button is disabled.
