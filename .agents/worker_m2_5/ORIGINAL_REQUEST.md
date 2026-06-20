# Task description for Worker 5 (Milestone 2)

## Goal
Implement the lifecycle-aware permission state observer in `SettingsScreen.kt` which Worker 4 claimed to implement but did not fully write to the codebase, and verify that the tests compile and pass.

## Required Code Modifications
1. **SettingsScreen.kt (`app/src/main/java/com/hush/app/ui/screens/settings/SettingsScreen.kt`)**:
   - Declare `isNotificationActive` and `isVoiceActive` as Composable state variables:
     ```kotlin
     var isNotificationActive by remember { mutableStateOf(false) }
     var isVoiceActive by remember { mutableStateOf(false) }
     ```
   - Implement a lifecycle-aware observer (`ON_RESUME`) using `DisposableEffect` and `LocalLifecycleOwner.current` to dynamically query and update `isNotificationActive = permissionManager.hasNotificationAccess()` and `isVoiceActive = permissionManager.hasMicrophonePermission()`.
   - Update the UI status text components to use these state variables:
     - `text = if (isNotificationActive) "Active" else "Inactive"`
     - `text = if (isVoiceActive) "Active" else "Inactive"`
   - Update the color of the status texts dynamically (e.g. green/primary for Active, red/error for Inactive).
   - Ensure the following imports are added:
     ```kotlin
     import androidx.compose.ui.platform.LocalLifecycleOwner
     import androidx.lifecycle.Lifecycle
     import androidx.lifecycle.LifecycleEventObserver
     ```

## Verification
- Run the build and connected instrumented tests:
  ```bash
  JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ./gradlew clean assembleDebug connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hush.app.e2e.NotificationInterceptionE2ETest,com.hush.app.e2e.RuleManagementHistoryE2ETest
  ```
- Ensure that the build is successful and all 20 tests pass.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## 2026-06-20T16:45:49Z
<USER_REQUEST>
You are Worker 5 for Milestone 2.
Your working directory is /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/worker_m2_5/.
Read ORIGINAL_REQUEST.md in your working directory to understand the task.
Implement the lifecycle-aware permission state observer in SettingsScreen.kt as described.
Verify that the tests compile and pass.
Upon completion, write a detailed handoff.md in your working directory and notify me (conversation ID: 8d9c850f-f31d-4804-ae75-009415fb81f3) by calling send_message.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.
</USER_REQUEST>
