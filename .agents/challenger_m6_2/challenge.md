# Challenge Report — Milestone 6 Validation

## Challenge Summary

**Overall risk assessment**: MEDIUM (due to build compilation issues under directories containing spaces, and test process crash)

## Challenges

### [Medium] Challenge 1: Path Space Bug in KSP/Hilt Annotation Processors
- **Assumption challenged**: The project's build and test framework assumes it can run in any workspace path.
- **Attack scenario**: When the project is checked out to a path containing spaces (e.g., `/Users/vipinsingh/Documents/Antigravity/open source/hush`), running `./gradlew connectedAndroidTest` or unit tests causes KSP to fail during compilation. 
  - If `ksp.incremental=false`, KSP throws `FileAlreadyExistsException` due to duplicating generated code paths.
  - If `ksp.incremental=true`, KSP throws `NoSuchFileException` looking for generated test classes.
- **Blast radius**: Local E2E tests and compilation of tests fail when project paths contain spaces.
- **Mitigation**: Update KSP and Kotlin versions in `libs.versions.toml` to a combination that properly handles space characters in paths, or restrict the project layout to space-free paths.

### [Medium] Challenge 2: E2E Test Suite Instrumentation Process Crash
- **Assumption challenged**: The entire E2E test suite executes to completion with exit code 0.
- **Attack scenario**: Running `./gradlew connectedAndroidTest` results in the instrumentation process crashing during the `testChat_VoiceCommand_StartsRecordingAndTranscribes` test execution:
  ```
  com.hush.app.e2e.ConversationalAIE2ETest > testChat_VoiceCommand_StartsRecordingAndTranscribes[test_device(AVD) - 15] FAILED 
  Test run failed to complete. Instrumentation run failed due to Process crashed.
  ```
- **Blast radius**: Only 20 out of 55 tests could finish. The rest of the suite was aborted.
- **Mitigation**: Investigate the fake or real `SpeechRecognizerWrapper` mock in `ConversationalAIE2ETest.kt` to identify thread-safety or lifecycle issues causing the instrumentation process crash when starting/stopping recording.

### [Low] Challenge 3: Lack of Debounce on Manual DB Retention Toggles
- **Assumption challenged**: Manual retention policy changes trigger a single safe DB deletion.
- **Attack scenario**: In `SettingsScreen.kt`, the user can toggle the retention policy. A quick succession of clicks on "7 Days", "30 Days", and "90 Days" triggers multiple concurrent coroutines running database deletion commands (`historyRepository.deleteLogsOlderThan(...)`).
- **Blast radius**: Unnecessary SQLite database write operations running in parallel.
- **Mitigation**: Debounce the database pruning triggers or keep track of the active pruning job to cancel previous ones before launching a new one.

---

## Stress Test Results

- **Onboarding Sliding Transition Validation** → Confirm that horizontal slide transitions occur based on step changes (`AnimatedContent`) → Code inspected and verified (`WelcomeStep` slides in/out forward and backward using direction-based slide animations) → **PASS**
- **Warning Banner Fade-in Validation** → Confirm that the warning banner uses fade-in animations on denial → Code inspected and verified (`AnimatedVisibility` uses `fadeIn` and `fadeOut` with `tween(500)`) → **PASS**
- **Theme Toggling Validation** → Verify that theme toggles instantly and persists across recreation → Verified that Shared Preferences and `OnSharedPreferenceChangeListener` are used to update configuration instantly and persist across recreation → **PASS**
- **Settings DB Retention Pruning Validation** → Verify that DB pruning is triggered on startup and manual settings click, writing exact logcat messages → Verified that `MainViewModel` (`init`) and `SettingsViewModel` (`pruneDatabase`) write `"Database retention pruning triggered"` and `"Manual retention pruning triggered"` respectively, and invoke Room deletion → **PASS**
- **E2E Test Execution Validation** → Run `./gradlew connectedAndroidTest` to execute all E2E tests → Run aborted after 20/55 tests due to a crash in `testChat_VoiceCommand_StartsRecordingAndTranscribes`, followed by KSP build failures under space path → **FAIL**

---

## Unchallenged Areas

- **Gemini Nano actual on-device generation** — The local environment uses a mocked `FakeAIEngine` for E2E tests, so the actual Gemini Nano integration (via AI Core) was not stress-tested.
