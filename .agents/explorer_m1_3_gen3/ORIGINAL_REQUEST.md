## 2026-06-20T05:41:32Z
You are Explorer 3 (Gen 3) for Milestone 1 (Project Skeleton) of the Hush Android app.
Your working directory is: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/explorer_m1_3_gen3/

Your mission is to perform a read-only exploration and propose a clean, compliant fix strategy for the following three issues identified in the Gen 2 Forensic Audit and Reviewer findings:

1. Hardcoded Mock Permission Bypass in Onboarding screen:
   - Location: `OnboardingScreen.kt` (lines 40-43, 53-58).
   - Problem: Production screen code contains mock state variables (`notificationGrantedMock`, etc.) that bypass actual system permission checks, compromising runtime safety.
   - Task: Decouple permission checking from the composable UI. Design a clean architecture solution, such as introducing a `PermissionManager` interface (with methods to check/request notifications, microphone, and battery optimization exemption status). Propose how to bind it with Hilt so that a real production implementation (which calls actual Android Context/system checks) is used normally, and a mock/fake implementation can be swapped in for instrumented tests.

2. Dynamic Theme Settings Facade:
   - Location: `MainActivity.kt` and `SettingsScreen.kt`.
   - Problem: Theme preferences are saved to SharedPreferences in `SettingsScreen.kt`, but `MainActivity` completely ignores this preference when calling `HushTheme`, so changing the setting has no effect on the app theme.
   - Task: Propose how to read the dynamic theme setting preference (e.g. "theme_option" SharedPreferences key) in `MainActivity.kt` and dynamically pass the correct `darkTheme` flag (or system default) to `HushTheme`.

3. Prop-Drilling in Compose Navigation Shell:
   - Location: `MainActivity.kt` (lines 23-50) and `HushNavigation.kt` / `MainScreen.kt`.
   - Problem: Repositories and wrappers like `AIEngine`, `SpeechRecognizerWrapper`, `RuleRepository`, `OnboardingPrefs` are injected into `MainActivity` and drilled down through parameters.
   - Task: Recommend how to refactor this using standard Hilt ViewModels or Dagger Hilt entry points in Composable destinations to inject repository classes where they are actually used.

Do NOT modify any code files directly (your role is read-only). You must write your findings and recommended strategy to:
`/Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/explorer_m1_3_gen3/analysis.md`
and write a handoff report to:
`/Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/explorer_m1_3_gen3/handoff.md`

Verify all proposed architectural structures, ViewModels, and Compose layouts. Report back when done.
