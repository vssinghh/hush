## 2026-06-20T18:19:21Z

You are Worker 3 for Milestone 4 (AI Integration).
Your task is to implement robustness fixes in `app/src/main/java/com/hush/app/data/repository/AIEngineImpl.kt`.

MANDATORY INTEGRITY WARNING — you MUST follow this instruction strictly:
"DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected."

### Robustness Fixes to Implement:
1. **Input Guard Check**:
   - At the beginning of `parseCommand(prompt: String)`, throw `IllegalArgumentException("Prompt cannot be blank")` if `prompt` is empty or blank.

2. **Availability Race Condition Mitigation**:
   - In `parseCommand`, if `isAvailableCached` is false, perform a dynamic check using `modelClient.isAvailable().await()` to see if AICore is available now. Cache it and proceed. Throw `IllegalStateException("AICore unavailable")` only if both checks fail.

3. **Markdown JSON Enclosure Clean Up**:
   - In `parseCommand`, locate the first `{` and last `}` character in the response text from the model.
   - If not found or if the start index is greater than or equal to the end index, throw `IllegalArgumentException("Model response did not contain a valid JSON object")`.
   - Otherwise, extract the substring between these two characters (inclusive) and pass it to `JSONObject` to prevent crashes when Gemini Nano wraps its response in markdown ` ```json ` blocks.

4. **Robust Time Parsing**:
   - In `parseCommand`, when parsing `timeStartStr` and `timeEndStr`, implement a helper function that attempts to parse the time string using multiple standard formats to handle variation (e.g. `"HH:mm"`, `"H:mm"`, `"HH:mm:ss"`, `"h:mm a"`, `"hh:mm a"`).
   - Use `Locale.ENGLISH` for formatters with AM/PM markers to prevent locale-specific crashes.
   - Fall back to returning null if all formats fail.

5. **Semantic Exception Mapping**:
   - Differentiate parsing issues from system/network issues:
     - Wrap `JSONException` (and JSON formatting errors) in `IllegalArgumentException("Malformed JSON from AI model: ...", e)`.
     - Wrap general SDK errors, model execution failures, or tasks connection errors in `IllegalStateException("AI engine failure: ...", e)`.

### Verification:
Compile the application and run unit tests and E2E tests:
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest`
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew connectedDebugAndroidTest`
Verify that all tests continue to pass. Document your changes in your handoff report.
Your working directory is /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/teamwork_preview_worker_m4_3/.
