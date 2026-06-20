# Original User Request

## 2026-06-20T17:15:28Z
You are the Sub-orchestrator for Milestone 4 (AI Integration) of the Hush Android app project.
Your task is to orchestrate the implementation of on-device AI natural language command parsing using Gemini Nano via AICore. This includes:
1. Setting up Google Play Services Generative AI/AICore dependency in build.gradle.kts / libs.versions.toml.
2. Implementing isAvailable() check and error fallback in AIEngineImpl.kt.
3. Designing the system prompt templates in PromptTemplates.kt/AIEngineImpl.kt.
4. Implementing ParseCommandUseCase.kt to process and validate Gemini Nano JSON outputs into ParsedCommand.
5. Building the package resolver mapping application names to package names.

Your working directory is /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m4/.
Read SCOPE.md and progress.md in that directory. Also read:
- /Users/vipinsingh/Documents/Antigravity/open source/hush/PROJECT.md
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/androidTest/java/com/hush/app/e2e/ConversationalAIE2ETest.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/domain/repository/AIEngine.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/data/repository/AIEngineImpl.kt

Follow the sub-orchestrator guidelines: decompose the milestone scope, iterate through Explorer -> Worker -> Reviewer -> Challenger -> Auditor to write the code and verify it builds and passes tests successfully.
Always ensure you run the Forensic Auditor (teamwork_preview_auditor) on each iteration. Verify that a CLEAN verdict is obtained.
MANDATORY INTEGRITY WARNING — you MUST include this verbatim in all Worker dispatch prompts:
"DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected."

You may use the android-cli skill at /Users/vipinsingh/.gemini/config/plugins/android-cli-plugin/skills/SKILL.md.
Keep your parent (conversation ID: 9b6df978-7864-42b8-8d7e-454e5aeb834f) updated on your progress by updating progress.md and sending status updates.
