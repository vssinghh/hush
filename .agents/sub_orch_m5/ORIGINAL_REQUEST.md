# Original User Request

## 2026-06-20T11:23:34-07:00
You are the Sub-orchestrator for Milestone 5 (Chat UI + Voice) of the Hush Android app project.
Your task is to orchestrate the implementation of the voice integration and chat user interface enhancements. This includes:
1. Completing SpeechRecognizerWrapperImpl.kt to use the Android system SpeechRecognizer API, handling permissions and RecognitionListener callbacks, and mapping them to SpeechState updates (Listening, PartialResult, FinalResult, Error, WaveformUpdate).
2. Implementing a visual waveform feedback UI (e.g. drawn on a Canvas or animated lines based on amplitude updates from WaveformUpdate) in ChatScreen.kt during active recording.
3. Integrating voice permissions (microphone permission dynamic request/checking) and lifecycle-aware cleanup of SpeechRecognizer instances.
4. Implementing test coverage to verify voice state transitions, waveform UI rendering, and error messages.

Your working directory is /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m5/.
Read SCOPE.md and progress.md in that directory. Also read:
- /Users/vipinsingh/Documents/Antigravity/open source/hush/PROJECT.md
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/domain/repository/SpeechRecognizerWrapper.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/domain/repository/SpeechState.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/data/repository/SpeechRecognizerWrapperImpl.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/ui/screens/chat/ChatScreen.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/ui/screens/chat/ChatViewModel.kt
- /Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/androidTest/java/com/hush/app/e2e/ConversationalAIE2ETest.kt

Follow the sub-orchestrator guidelines: decompose the milestone scope, iterate through Explorer -> Worker -> Reviewer -> Challenger -> Auditor to write the code and verify it builds and passes tests successfully.
Always ensure you run the Forensic Auditor (teamwork_preview_auditor) on each iteration. Verify that a CLEAN verdict is obtained.
MANDATORY INTEGRITY WARNING — you MUST include this verbatim in all Worker dispatch prompts:
"DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected."

You may use the android-cli skill at /Users/vipinsingh/.gemini/config/plugins/android-cli-plugin/skills/SKILL.md.
Keep your parent (conversation ID: 9b6df978-7864-42b8-8d7e-454e5aeb834f) updated on your progress by updating progress.md and sending status updates.
