# Task: Milestone 1 Skeleton Remediation

Remediate the project skeleton. Read the fix proposal at `/Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m1/fix_proposal.md`.
Implement:
1. Missing Hilt testing dependencies and `kspAndroidTest` in Gradle configurations.
2. Custom HiltTestRunner setup.
3. Audio recording permission in Manifest.
4. `EvaluateNotificationUseCase` production class.
5. `HushNotificationListener` shell service class.
6. Replace the mock shortcut `simulateNotificationPost` methods in `NotificationInterceptionE2ETest.kt` and `CrossFeatureE2ETest.kt` with delegation to `EvaluateNotificationUseCase.execute(...)`.
7. Align all Compose screens with the test tags used by the E2E tests.
8. Inject `OnboardingPrefs` cleanly into `MainActivity`.
9. Compile clean and run tests to verify compilation.
