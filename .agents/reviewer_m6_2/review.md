# Review Report — Milestone 6 (Onboarding & Polish)

## Review Summary

**Verdict**: REQUEST_CHANGES

The implementation by `worker_m6_1` contains high-quality visual polish, correct permission persistence logic, and a solid light-theme selection implementation with corresponding E2E test coverage. However, there are thread safety issues regarding database pruning coroutines running on the main thread dispatcher, and the test suite execution suffers from flakiness/crashes due to lifecycle conflicts in other E2E test classes.

---

## Findings

### [Major] Finding 1: Database Pruning Coroutines run on Main Thread Dispatcher

- **What**: The coroutines launched for database pruning do not explicitly specify the background dispatcher (`Dispatchers.IO`).
- **Where**:
  - `app/src/main/java/com/hush/app/MainViewModel.kt` (lines 49-56)
  - `app/src/main/java/com/hush/app/ui/screens/settings/SettingsScreen.kt` (lines 75-82)
- **Why**: By default, `viewModelScope.launch` and `coroutineScope.launch` (which uses `rememberCoroutineScope()`) execute on the Main thread dispatcher (`Dispatchers.Main.immediate` / `Dispatchers.Main`). While Room's generated suspend functions offload execution to a background pool internally, any wrapping logic, parameters computation (like `Instant.now()`), or logging statements run on the Main thread. Explicitly offloading database clean-ups to `Dispatchers.IO` is standard robust practice.
- **Suggestion**: Change the coroutine builders to target `Dispatchers.IO` explicitly:
  - In `MainViewModel.kt`: `viewModelScope.launch(Dispatchers.IO) { ... }`
  - In `SettingsScreen.kt`: `coroutineScope.launch(Dispatchers.IO) { ... }` (importing `kotlinx.coroutines.Dispatchers`).

### [Major] Finding 2: Instrumentation Process Crash during Test Suite Execution

- **What**: Running the full test suite (`./gradlew connectedAndroidTest`) frequently crashes due to a process crash (`Process crashed`) during the execution of `CrossFeatureE2ETest` or `ConversationalAIE2ETest`.
- **Where**:
  - `app/src/androidTest/java/com/hush/app/e2e/CrossFeatureE2ETest.kt` (setup / teardown lifecycle)
  - `app/src/androidTest/java/com/hush/app/e2e/ConversationalAIE2ETest.kt` (setup / teardown lifecycle)
- **Why**: Both test classes declare `createAndroidComposeRule<MainActivity>()`, which automatically manages and launches `MainActivity`. However, in their `@Before setup()`, they manually call `activeScenario?.close()` and launch another instance via `ActivityScenario.launch(intent)`. This duplicate lifecycle management puts the app process temporarily in the background, causing the Android OS App Freezer to freeze the process. When subsequent binder actions are dispatched, the frozen process is killed with `Signal 9 (SIGKILL)`, resulting in a crashed instrumentation run.
- **Suggestion**: Avoid using manual `ActivityScenario.launch` and `activeScenario?.close()` in classes that define `createAndroidComposeRule<MainActivity>()`. To set preferences (like onboarding completed) before launch, either:
  1. Use `createEmptyComposeRule()` and manage activity launch manually.
  2. Or restructure onboarding verification to avoid recreating/relaunching activities during test setup.

---

## Verified Claims

- **Permission Denial State Persistence** → Verified via code review of `PermissionManagerImpl.kt` → **PASS**
  - Persistence logic uses private SharedPreferences (`"hush_preferences"`) and key (`"notification_access_denied"`). Active access dynamically overrides the denied state.
- **Light Theme Option & E2E Test** → Verified via code review and running `AppFoundationE2ETest` → **PASS**
  - "Light Theme" option is fully supported, persisted in SharedPreferences (`"theme_option"`), and verified end-to-end via `testSettingsScreen_ToggleLightTheme_ThemeChangesAndPersists()` which recreates the activity and asserts the theme persists.
- **Visual Polish & Ripple Clipping** → Verified via code review of `ChatScreen.kt` and `OnboardingScreen.kt` → **PASS**
  - The send and mic buttons are properly clipped via `.clip(CircleShape)` prior to applying `.background(...)`, preventing ripple bounds bleed-out. Warning banners utilize `AnimatedVisibility` with fade/vertical slide animations.

---

## Coverage Gaps

- No coverage gaps identified. The newly added test case directly addresses light theme persistence.

---

## Unverified Items

- None. All files and claims have been examined and verified using code analysis and local instrumentation runner (`adb shell am instrument`).
