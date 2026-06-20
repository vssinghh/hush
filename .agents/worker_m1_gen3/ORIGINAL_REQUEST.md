## 2026-06-19T22:46:54-07:00
You are Worker (Gen 3) for Milestone 1 (Project Skeleton) of the Hush Android app.
Your working directory is: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/worker_m1_gen3/

Your task is to implement the code changes and refactoring detailed in the synthesized plan and fix proposal documents:
1. Read the fix proposal: `/Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m1/fix_proposal.md`
2. Read the architecture plan: `/Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m1/synthesis.md`

Specifically, you must:
1. Add `androidTestImplementation(libs.androidx.espresso.intents)` to `app/build.gradle.kts` and delete the fake stubs inside `app/src/androidTest/java/androidx/test/espresso/intent/`.
2. Enable schema exporting in Room (`exportSchema = true` in `HushDatabase.kt`) and create the `app/schemas` directory.
3. Abstract onboarding screen permissions behind a Hilt-injectable `PermissionManager` interface, implementing `PermissionManagerImpl` (real system checks) and `FakePermissionManager` (for instrumented tests). Manage permission request states dynamically inside a new `OnboardingViewModel`.
4. Create a `MainViewModel` that registers an `OnSharedPreferenceChangeListener` to observe `"theme_option"` and exposes it via `StateFlow` to `MainActivity.kt`, allowing dynamic, mid-session theme updates.
5. Eliminate direct repository injections from `MainActivity.kt` parameter passing. Inject them directly using `hiltViewModel()` inside Composable screens (e.g. `ChatScreen` with `ChatViewModel`).
6. Implement boundary-inclusive time window checking (daytime and overnight) inside the production `EvaluateNotificationUseCase.kt`, and only write notification history logs when a rule matches (`matchedRuleId != null`).
7. Delegate simulated notification evaluation in `RealWorldScenarioE2ETest.kt` (`simulateNotificationPost`) directly to `EvaluateNotificationUseCase.execute(..., currentTime)`.
8. Update test assertions inside `NotificationInterceptionE2ETest.kt` and `RealWorldScenarioE2ETest.kt` to reflect 0 logs for allowed notifications that do not match rule targets.
9. Refactor `testInterception_RapidConcurrentNotifications_ThreadSafety` to use `async(Dispatchers.Default)` and `jobs.awaitAll()` to test actual concurrent database insertion without arbitrary sleep delays.

MANDATORY INTEGRITY WARNING:
> DO NOT CHEAT. All implementations must be genuine. DO NOT
> hardcode test results, create dummy/facade implementations, or
> circumvent the intended task. A Forensic Auditor will independently
> verify your work. Integrity violations WILL be detected and your
> work WILL be rejected.

After applying the changes:
1. Run the build command to verify debug compilation:
   `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew assembleDebug`
2. Run the test compilation command:
   `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew compileDebugAndroidTestSources`
3. Run the unit tests:
   `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ./gradlew testDebugUnitTest`

Write your handoff report containing the commands executed, build/test results, and changed files list to:
`/Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/worker_m1_gen3/handoff.md`

Report back when the implementation and validation are completed.
