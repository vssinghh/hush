## 2026-06-20T18:46:41Z
You are the Worker 2 for Milestone 6 (Onboarding & Polish) of the Hush app.
Your task is to implement the fixes requested by the reviewers to resolve all quality, architectural, and test stability issues:

1. **Remove Test Backdoor in Production Code & Implement Real Permission Denial Detection**:
   - In `app/src/main/java/com/hush/app/ui/screens/onboarding/OnboardingScreen.kt`:
     - Remove the hidden `Box` test-tagged `"onboarding_grant_notification_deny_mock"` (lines 219-224).
     - Remove the unused private helper functions `isNotificationServiceEnabled` and `isIgnoringBatteryOptimizations` at the bottom of the file (lines 362-372).
     - In the ON_RESUME Lifecycle observer block (lines 43-55), add a check: if `viewModel.isNotificationPermissionRequested` is true AND `viewModel.hasNotificationAccess` is false, call `viewModel.denyNotificationAccess()`. Then call `viewModel.refreshPermissions()`.
     - In the root Scaffold column, wrap the `when (currentStep)` block with Compose's `AnimatedContent` for sliding transitions between steps.
       Example:
       ```kotlin
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
           when (step) {
               0 -> WelcomeStep(onNext = { currentStep = 1 })
               1 -> PermissionsStep(...)
               2 -> AICoreStep(...)
           }
       }
       ```
       Ensure you add any required imports: `import androidx.compose.animation.*` and `import androidx.compose.animation.core.tween`.
     - Wrap the `onboarding_deny_rationale` text inside `AnimatedVisibility` for a smooth fade-in.
   - In `app/src/main/java/com/hush/app/ui/screens/onboarding/OnboardingViewModel.kt`:
     - Add a state variable `var isNotificationPermissionRequested by mutableStateOf(false)`.
     - In `requestNotificationAccess(context: Context)`, set `isNotificationPermissionRequested = true` before requesting.

2. **Refactor Settings Screen to follow MVVM & Dispatchers.IO**:
   - Create a Hilt-injected ViewModel class: `app/src/main/java/com/hush/app/ui/screens/settings/SettingsViewModel.kt`
     - It should inject `HistoryRepository` and `PermissionManager`.
     - Expose state flows for `isNotificationActive` and `isVoiceActive`.
     - Implement a function `pruneDatabase(policy: String)` which launches a coroutine on `Dispatchers.IO` (using `viewModelScope.launch(Dispatchers.IO)`) to prune the DB and log `Log.d("HushPruning", "Manual retention pruning triggered: deleted logs older than $threshold")`.
   - Update `app/src/main/java/com/hush/app/ui/screens/settings/SettingsScreen.kt`:
     - Inject `SettingsViewModel` via constructor / `hiltViewModel()`.
     - Remove the `@EntryPoint` and direct application entry points.
     - Observe the notification and voice states from the viewmodel.
     - Let the lifecycle ON_RESUME observer call `viewModel.refreshPermissions()`.
     - In the retention button click callbacks, invoke `viewModel.pruneDatabase(policy)`.

3. **Thread Safety for Startup Database Pruning**:
   - In `app/src/main/java/com/hush/app/MainViewModel.kt`, ensure that `pruneDatabaseOnStartup` launches its coroutine on `Dispatchers.IO` (`viewModelScope.launch(Dispatchers.IO)`).

4. **Visual Polish (M3 FilledIconButton & Ripples)**:
   - In `app/src/main/java/com/hush/app/ui/screens/chat/ChatScreen.kt`:
     - Replace the customSend and Mic `IconButton`s with standard Material 3 `FilledIconButton` components.
     - Remove custom `.clip(CircleShape)` and `.background(...)` modifiers from them, as M3 `FilledIconButton` handles circles and backgrounds natively, and automatically applies appropriate disabled colors when `enabled = false`.

5. **Test Stability & Leak Fixes (createEmptyComposeRule)**:
   - In the following E2E test files:
     - `app/src/androidTest/java/com/hush/app/e2e/AppFoundationE2ETest.kt`
     - `app/src/androidTest/java/com/hush/app/e2e/ConversationalAIE2ETest.kt`
     - `app/src/androidTest/java/com/hush/app/e2e/CrossFeatureE2ETest.kt`
     - `app/src/androidTest/java/com/hush/app/e2e/RealWorldScenarioE2ETest.kt`
   - Modify the compose rule definition:
     Change `var composeRule = createAndroidComposeRule<MainActivity>()` to `var composeRule = createEmptyComposeRule()`.
   - Replace import statement:
     Change `import androidx.compose.ui.test.junit4.createAndroidComposeRule` to `import androidx.compose.ui.test.createEmptyComposeRule` (or let it wildcard-import `androidx.compose.ui.test.*`).
   - Update tests to ensure `activeScenario` is launched manually via `ActivityScenario.launch<MainActivity>(intent)` where required, and that `activeScenario` is closed in `tearDown()`. Change `composeRule.activityRule.scenario.recreate()` to `activeScenario?.recreate()`.
   - In `AppFoundationE2ETest.kt`, update `testOnboarding_DenyNotificationAccess_ShowsRationaleAndDisablesNext`:
     - Remove the call to click `onboarding_grant_notification_deny_mock`.
     - Instead, click `onboarding_grant_notification` (to set `isNotificationPermissionRequested = true`).
     - Set `(permissionManager as FakePermissionManager).apply { notificationGranted = false; notificationDenied = true }`.
     - Recreate activity using `recreateActivityAndWait("onboarding_screen", freshLaunch = false)` to trigger `ON_RESUME`.
     - Assert that the rationale is shown and the next button is disabled.

6. **Verify and Compile**:
   - Compile the project and run the instrumentation tests (`./gradlew connectedAndroidTest`) to ensure all 55 tests pass cleanly.
