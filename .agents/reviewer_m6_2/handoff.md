# Handoff Report — Milestone 6 (Onboarding & Polish) Review

## 1. Observation

- **Modified Files Reviewed**:
  - `app/src/main/java/com/hush/app/data/repository/PermissionManagerImpl.kt`
  - `app/src/main/java/com/hush/app/ui/screens/settings/SettingsScreen.kt`
  - `app/src/main/java/com/hush/app/MainViewModel.kt`
  - `app/src/main/java/com/hush/app/ui/navigation/HushNavigation.kt`
  - `app/src/main/java/com/hush/app/ui/screens/MainScreen.kt`
  - `app/src/main/java/com/hush/app/ui/screens/chat/ChatScreen.kt`
  - `app/src/main/java/com/hush/app/ui/screens/onboarding/OnboardingScreen.kt`
  - `app/src/androidTest/java/com/hush/app/e2e/AppFoundationE2ETest.kt`

- **Command Outputs**:
  - Running single class `AppFoundationE2ETest` succeeded:
    ```
    Starting 11 tests on test_device(AVD) - 15
    Finished 11 tests on test_device(AVD) - 15
    BUILD SUCCESSFUL in 19s
    ```
  - Running the complete test suite `./gradlew connectedAndroidTest` failed:
    ```
    test_device(AVD) - 15 Tests 23/55 completed. (0 skipped) (0 failed)
    com.hush.app.e2e.CrossFeatureE2ETest > testCombination_RuleDeletion_To_HistoryLogsGracefulDisplay[test_device(AVD) - 15] FAILED
    Tests on test_device(AVD) - 15 failed: There was 1 failure(s).
    Finished 25 tests on test_device(AVD) - 15
    BUILD FAILED
    Test run failed to complete. Instrumentation run failed due to Process crashed.
    ```

- **Logcat Output**:
  ```
  06-20 02:35:37.799   369   369 I Zygote  : Process 1721 exited due to signal 9 (Killed)
  06-20 02:35:37.810   564   603 I libprocessgroup: Removed cgroup /sys/fs/cgroup/uid_10492/pid_1721
  06-20 02:35:37.811   564  1657 V ActivityManager: Got obituary of 1721:com.hush.app
  ```

---

## 2. Logic Chain

- **Thread Safety (Database Pruning)**:
  - Observation: `MainViewModel.kt` (line 49) starts database pruning using `viewModelScope.launch` with no dispatcher argument. `SettingsScreen.kt` (line 75) does the same using `coroutineScope.launch`.
  - Logic: In Jetpack Compose, the default coroutine context runs on `Dispatchers.Main.immediate` or `Dispatchers.Main`. Launching database cleanups on the main thread is unsafe. Although Room's generated suspend functions internally use a background thread, any wrapping parameters preparation (e.g. `Instant.now()`) or logging statements execute on the Main UI thread.
  - Conclusion: Database pruning must explicitly run on `Dispatchers.IO`.

- **Test Suite Process Crash**:
  - Observation: The full suite fails with `Process crashed` during `CrossFeatureE2ETest` or `ConversationalAIE2ETest`.
  - Logic: These test classes declare `createAndroidComposeRule<MainActivity>()` which automatically starts the main activity. Inside `@Before setup()`, they also manually call `activeScenario?.close()` and launch another activity instance via `ActivityScenario.launch`. This duplicate lifecycle management forces the app process to the background, prompting the Android OS App Freezer to freeze the process. When subsequent binder calls occur, the frozen process is terminated with `Signal 9 (SIGKILL)`, causing the instrumentation run to fail.
  - Conclusion: The test class lifecycles conflict with Compose rules and must be resolved.

---

## 3. Caveats

- No caveats.

---

## 4. Conclusion

- **Verdict**: `REQUEST_CHANGES` to fix:
  1. Thread safety by adding `Dispatchers.IO` to database pruning coroutine builders.
  2. Instrumentation process crashes caused by conflicting lifecycles in `CrossFeatureE2ETest` and `ConversationalAIE2ETest`.

---

## 5. Verification Method

- Run only the modified E2E tests:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hush.app.e2e.AppFoundationE2ETest
  ```
- Run the full test suite:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew connectedAndroidTest
  ```
- Verify Logcat outputs contain:
  `D/HushPruning: Database retention pruning triggered...`
