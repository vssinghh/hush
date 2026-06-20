# Handoff Report: Milestone 4 (AI Integration) - Explorer 2

## 1. Observation
1.  **File Location and Current Skeleton**: The skeleton class `AIEngineImpl.kt` is located at `/Users/vipinsingh/Documents/Antigravity/open source/hush/app/src/main/java/com/hush/app/data/repository/AIEngineImpl.kt`. It implements `AIEngine` from `com.hush.app.domain.repository.AIEngine` (lines 5-8).
    *   Currently, it returns static defaults: `override fun isAvailable(): Boolean = false` (line 13) and `parseCommand(prompt: String)` returns a static `ParsedCommand` with `summary = "Not implemented"` (lines 15-27).
2.  **Synchronous Interface and UI Invocation**: `AIEngine.isAvailable()` is a synchronous, non-suspending method. It is invoked directly during Compose rendering in `ChatScreen.kt` (lines 129, 330, 341, 345, 364, 368) to enable/disable UI controls and display warnings.
3.  **UI App Installation Checks**: In `ChatScreen.kt`, lines 235 and 252-259 verify package installation status using a helper:
    *   `val isInstalled = rule.app?.let { isAppInstalled(context, it) } ?: true` (line 235)
    *   This helper utilizes `context.packageManager.getPackageInfo(packageName, 0)` (lines 377-384).
4.  **E2E Test Specifications**: `ConversationalAIE2ETest.kt` defines two critical test cases for parsing errors:
    *   `testChat_MalformedJsonFromAI_ShowsErrorMessage` (lines 185-206) expects a `chat_error_message` display tag when the AI model returns malformed JSON or triggers a parsing error.
    *   `testChat_UnresolvedAppName_DefaultsToNullPackage` (lines 209-230) expects a warning card with `ai_rule_warning_uninstalled` when the package is not installed on the system (e.g. returns `"com.customapp.uninstalled"`).
5.  **Manifest Configuration**: The main application manifest `app/src/main/AndroidManifest.xml` does not contain a `<queries>` declaration or `QUERY_ALL_PACKAGES` permission.

## 2. Logic Chain
1.  **Requirement (1) - Google Play Services Generative AI SDK APIs**:
    *   The `com.google.android.gms:play-services-generativeai` package exposes the `com.google.android.gms.generativeai.GenerativeModel` and `com.google.android.gms.generativeai.GenerationConfig` classes.
    *   We configure generation via `GenerationConfig.builder().setTemperature(0.0f).setResponseMimeType("application/json")` to restrict Gemini Nano's output format to JSON.
    *   We construct the model using `GenerativeModel.Builder(context)` specifying model name `"gemini-nano"` and system instructions.
    *   `generativeModel.generateContent(prompt).await()` (via `kotlinx-coroutines-play-services`) is called to execute generation in a suspend-friendly manner.
2.  **Requirement (2) - AICore checking and exception handling in `AIEngineImpl.kt`**:
    *   `GenerativeModelClient.isAvailable()` returns a GMS `Task<Boolean>`.
    *   It can throw `ApiException`, `UnsupportedOperationException`, and `IllegalStateException`.
    *   Because `AIEngine.isAvailable()` is synchronous and called within Compose rendering loops (Obs 2), calling blocking `Tasks.await()` would lock the main thread, risking ANRs.
    *   **Conclusion**: We must perform the check asynchronously in `AIEngineImpl`'s `init {}` block, catch all exceptions, log them, and cache the availability status in a thread-safe `@Volatile var isAvailableCached: Boolean`.
3.  **Requirement (3) - Dynamic Prompt Design and App Resolution**:
    *   In Android 11+ (API 30+), querying the package manager returns a restricted package list unless the app has appropriate package visibility declarations (Obs 5).
    *   We need to add `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` to `AndroidManifest.xml` so the package manager can correctly compile all installed application names and package names.
    *   The compiled list of installed applications (e.g. `"- App Name: \"WhatsApp\", Package Name: \"com.whatsapp\""`) is dynamically formatted and prepended to the user command context in the prompt template.
    *   This instructs Gemini Nano to resolve the spoken app name directly to the corresponding package name from the prepended list. If the app is not in the list, the prompt instructs the model to fallback to generating a `com.<app>.uninstalled` naming pattern, which is then captured by the UI's installation validator (Obs 3).

## 3. Caveats
*   The actual hardware behavior of Gemini Nano cannot be tested directly in a standard cloud CI environment or common emulators. The instrumented E2E test suite already includes a Hilt binding swap for `FakeAIEngine`, ensuring tests continue to run reliably on non-AICore-capable environments.
*   Google Play Store policies have strict guidelines around publishing apps with `QUERY_ALL_PACKAGES` permission. Since Hush functions as a local system notification filter manager, this fits the policy requirements, but must be declared and justified during publishing.

## 4. Conclusion
We have designed a complete, robust, and clean blueprint for the AI Integration Milestone:
1.  Add `com.google.android.gms:play-services-generativeai` and `kotlinx-coroutines-play-services` dependencies.
2.  Add `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` to `AndroidManifest.xml`.
3.  Implement asynchronous caching of `isAvailable()` in `AIEngineImpl` to prevent UI thread blockages.
4.  Design a prompt structure prepending package manager results to user queries, enforcing deterministic JSON output via system instructions and MIME constraint configs.
5.  All proposed implementation details are detailed in the `analysis.md` report.

## 5. Verification Method
1.  **Inspect Analysis Report**: Verify the class layout and prompt structure in `/Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/teamwork_preview_explorer_m4_2/analysis.md`.
2.  **Compilation & Test Suite**: The changes proposed in `analysis.md` can be integrated by the implementer. The implementation can then be verified by running the unit tests and instrumented tests:
    *   `./gradlew test` (Unit tests)
    *   `./gradlew connectedAndroidTest` (Instrumented E2E tests, which will mock AI but verify DI/skeleton bindings and parsing logic compatibility).
