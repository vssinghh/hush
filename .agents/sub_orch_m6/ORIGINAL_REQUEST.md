# Original User Request

## Initial Request — 2026-06-20T11:31:57-07:00

You are the Sub-orchestrator for Milestone 6 (Onboarding & Polish) of the Hush Android app project.
Your task is to orchestrate the implementation and polish of the onboarding, permissions flow, settings preferences, theme options persistence, and visual animations. This includes:
1. Verifying that the onboarding flow, permissions steps, rationales for denied permissions, and SharedPreferences storage function properly.
2. Integrating theme selection and persistence (Dark Theme, Light Theme, System Default) in settings and observing user preferences immediately in MainActivity/Theme.kt.
3. Ensuring settings DB retention pruning triggers delete logs correctly.
4. Adding polished navigation screen transitions, button ripples, and warning banner fade-ins.
5. Implementing test coverage to verify onboarding, settings preferences, theme persistence, and rationales.

Your working directory is /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m6/.
Read SCOPE.md and progress.md in that directory. Also read:
- /Users/vipinsingh/Documents/Antigravity/open source/hush/PROJECT.md
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/ui/screens/onboarding/OnboardingScreen.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/ui/screens/onboarding/OnboardingViewModel.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/ui/screens/settings/SettingsScreen.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/androidTest/java/com/hush/app/e2e/AppFoundationE2ETest.kt

Follow the sub-orchestrator guidelines: decompose the milestone scope, iterate through Explorer -> Worker -> Reviewer -> Challenger -> Auditor to write the code and verify it builds and passes tests successfully.
Always ensure you run the Forensic Auditor (teamwork_preview_auditor) on each iteration. Verify that a CLEAN verdict is obtained.
MANDATORY INTEGRITY WARNING — you MUST include this verbatim in all Worker dispatch prompts:
"DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected."

You may use the android-cli skill at /Users/vipinsingh/.gemini/config/plugins/android-cli-plugin/skills/SKILL.md.
Keep your parent (conversation ID: 9b6df978-7864-42b8-8d7e-454e5aeb834f) updated on your progress by updating progress.md and sending status updates.
