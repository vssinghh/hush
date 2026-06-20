# Task description for Worker 4 (Milestone 2)

## Goal
Modify production code in the Hush Android app to address the facade/integrity violations identified by the Forensic Auditor, and verify that the app compiles and all tests pass.

## Required Code Modifications
1. **SettingsScreen.kt (`app/src/main/java/com/hush/app/ui/screens/settings/SettingsScreen.kt`)**:
   - Add `fun permissionManager(): PermissionManager` to the `SettingsEntryPoint` interface.
   - Inject/retrieve the `PermissionManager` using `EntryPointAccessors.fromApplication` inside `SettingsScreen`.
   - Implement lifecycle-aware observer (`ON_RESUME`) to dynamically query `permissionManager.hasNotificationAccess()` and `permissionManager.hasMicrophonePermission()`.
   - Update the UI to render "Active" or "Inactive" based on those dynamic permission values instead of hardcoding "Active".
   
2. **RulesScreen.kt (`app/src/main/java/com/hush/app/ui/screens/rules/RulesScreen.kt`)**:
   - Modify line 102 to use `rule.name` directly:
     ```kotlin
     val displayName = rule.name
     ```
     This fixes the weird erasure behavior where the rule name disappears from the list card when selected.

3. **FakePermissionManager.kt (`app/src/androidTest/java/com/hush/app/mock/FakePermissionManager.kt`)**:
   - Inject `@ApplicationContext private val context: Context` in the constructor.
   - Access the shared preferences `"hush_preferences"` to check the `"onboarding_completed"` key.
   - Return `true` for `hasNotificationAccess()`, `hasMicrophonePermission()`, and `isBatteryExempt()` if onboarding is completed, or default to the internal test variables.

## Verification
- Run the build and connected instrumented tests:
  ```bash
  JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ./gradlew clean assembleDebug connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hush.app.e2e.NotificationInterceptionE2ETest,com.hush.app.e2e.RuleManagementHistoryE2ETest
  ```
- Ensure that the build is successful and all 20 tests pass.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## 2026-06-20T16:38:54Z
You are Worker 4 for Milestone 2.
Your working directory is /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/worker_m2_4/.
Read ORIGINAL_REQUEST.md in your working directory to understand the task.
Implement the changes described in ORIGINAL_REQUEST.md to resolve the integrity violations, compile the app, and run the instrumented E2E tests.
Verify that the tests pass.
Upon completion, write a detailed handoff.md in your working directory and notify me (conversation ID: 8d9c850f-f31d-4804-ae75-009415fb81f3) by calling send_message.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.
