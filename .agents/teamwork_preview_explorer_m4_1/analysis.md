# Milestone 4: Google Play Services Generative AI (AICore) Integration Analysis

## Summary
This report analyzes the Hush project to integrate Google Play Services Generative AI (AICore / Gemini Nano) on-device. It details the required Gradle dependencies, provides a non-blocking design for `AIEngineImpl.isAvailable()`, proposes system prompt templates, establishes a robust parsing and validation pipeline inside a new `ParseCommandUseCase.kt`, and documents the project's testing structure.

---

## 1. Library Coordinates and Gradle Configuration

To integrate Gemini Nano via Google Play Services (AICore), the following dependencies must be added:

### Dependency declaration in `gradle/libs.versions.toml`
Add the Play Services Generative AI library and the Kotlin Coroutines Play Services extension (necessary to support `.await()` on `Task` returns):
```toml
[versions]
# ... existing versions ...
play-services-generativeai = "16.0.0-beta01"
kotlinx-coroutines-play-services = "1.8.1"

[libraries]
# ... existing libraries ...
play-services-generativeai = { group = "com.google.android.gms", name = "play-services-generativeai", version.ref = "play-services-generativeai" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "kotlinx-coroutines-play-services" }
```

### Dependency usage in `app/build.gradle.kts`
Implement these libraries inside the `dependencies` block:
```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Google Play Services Generative AI (Gemini Nano)
    implementation(libs.play.services.generativeai)
    implementation(libs.kotlinx.coroutines.play-services)
}
```

---

## 2. Implementing `isAvailable()` and Fallbacks in `AIEngineImpl.kt`

### Observation of Current Code (`app/src/main/java/com/hush/app/data/repository/AIEngineImpl.kt`)
The current implementation of `AIEngineImpl` is stubbed as follows:
```kotlin
@Singleton
class AIEngineImpl @Inject constructor() : AIEngine {
    override fun isAvailable(): Boolean = false

    override suspend fun parseCommand(prompt: String): ParsedCommand {
        // Dummy implementation returning ParsedCommand
    }
}
```

### Proposing Non-blocking `isAvailable()`
In `ChatScreen.kt`, the UI calls `aiEngine.isAvailable()` directly during composition to toggle UI states (like input fields and the unsupported banner):
- Line 129: `if (!aiEngine.isAvailable())`
- Line 330: `enabled = aiEngine.isAvailable()`

Since checking AICore availability involves querying system services via Play Services (returning a `Task<FeatureAvailabilityResult>`), performing this check synchronously via `runBlocking` or `Tasks.await()` during recomposition would block the main thread and cause UI stuttering or ANRs.

**Proposed Solution**: Perform the check asynchronously in the repository's `init` block (or on app startup), cache the outcome in an `AtomicBoolean`, and return the cached value instantly in `isAvailable()`.

### Implementation Proposal:
```kotlin
package com.hush.app.data.repository

import android.content.Context
import com.google.android.gms.generativeai.FeatureAvailabilityResult
import com.google.android.gms.generativeai.GenerativeAI
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.repository.AIEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AIEngine {

    private val isModelAvailable = AtomicBoolean(false)
    private val client by lazy { GenerativeAI.getClient(context) }

    init {
        checkAvailabilityAsync()
    }

    private fun checkAvailabilityAsync() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val availability = client.checkFeatureAvailability().await()
                isModelAvailable.set(availability == FeatureAvailabilityResult.STATUS_AVAILABLE)
            } catch (e: Exception) {
                isModelAvailable.set(false)
            }
        }
    }

    override fun isAvailable(): Boolean = isModelAvailable.get()

    override suspend fun parseCommand(prompt: String): ParsedCommand {
        if (!isAvailable()) {
            throw IllegalStateException("Gemini Nano / AICore is not available on this device.")
        }
        
        // Setup prompt and call local inference
        val model = client.getGenerativeModel("gemini-nano")
        val response = model.generateContent(prompt)
        
        // Return raw JSON output from model; let ParseCommandUseCase parse & validate it.
        // Wait: The AIEngine interface requires returning ParsedCommand.
        // We will parse the output inside AIEngineImpl or delegate to ParseCommandUseCase.
        // (See Section 4 for UseCase delegation details).
    }
}
```

### Error Fallbacks
If the local model fails during generation (due to OOM, model updates, or syntax limits), the `parseCommand` method should throw a customized `AIParsingException`. The calling ViewModel can then catch this and set the `errorMessage` state, displaying a chat error bubble (`chat_error_message`) as required by the E2E tests.

---

## 3. System Prompt Templates and Output Format Constraints

### Storage Location
System prompt templates can be stored in one of two places:
1. **Kotlin Constants Object** (`app/src/main/java/com/hush/app/data/repository/AIPrompts.kt`): Very fast, simple, compile-time safe, and context-free.
2. **Assets Folder** (`app/src/main/assets/prompts/system_prompt.txt`): Better separation of concerns; allows updating the prompt without editing code. Requires a `Context` to read.

*Recommendation*: Store the system prompt as a Kotlin `const val` inside an `AIPrompts.kt` file within the data layer for simple and direct access without Context dependencies in repository constructors.

### System Prompt Template
To satisfy the JSON schema contract defined in `PROJECT.md` and satisfy the test requirements, the system prompt must explicitly enforce the output structure and provide few-shot examples:

```text
You are an AI assistant that parses natural language notification filtering commands into structured JSON rules for the Android app Hush.

Your output must be a single JSON object. Do not output any preamble, markdown code blocks (e.g., do not wrap in ```json), or explanation. Output ONLY the raw JSON.

JSON Schema:
{
  "action": "block" | "allow" | "mute",
  "app": "package.name" | null (e.g., "com.whatsapp" for WhatsApp, "com.slack" for Slack, "com.instagram.android" for Instagram, or null if no specific app is named),
  "matchField": "title" | "text" | "sender" | "any",
  "matchType": "contains" | "regex" | "exact",
  "matchPattern": "string" | null (the text or pattern to match; null if no pattern is specified),
  "isInverted": boolean (true if the rule should match everything EXCEPT the pattern, e.g., "mute whatsapp except from Bob" -> isInverted=true, matchPattern="Bob"),
  "timeStart": "HH:mm" | null (start of the active time window in 24-hour format, e.g., "22:00" or null),
  "timeEnd": "HH:mm" | null (end of the active time window in 24-hour format, e.g., "07:00" or null),
  "summary": "human-readable summary" (brief description of what the rule does)
}

Few-shot Examples:
User: "Mute WhatsApp notifications"
Output:
{"action":"mute","app":"com.whatsapp","matchField":"any","matchType":"contains","matchPattern":null,"isInverted":false,"timeStart":null,"timeEnd":null,"summary":"Mute WhatsApp notifications"}

User: "Mute WhatsApp except from Bob"
Output:
{"action":"mute","app":"com.whatsapp","matchField":"sender","matchType":"exact","matchPattern":"Bob","isInverted":true,"timeStart":null,"timeEnd":null,"summary":"Mute WhatsApp except from Bob"}

User: "Block notifications from Slack between 10 PM and 7 AM"
Output:
{"action":"block","app":"com.slack","matchField":"any","matchType":"contains","matchPattern":null,"isInverted":false,"timeStart":"22:00","timeEnd":"07:00","summary":"Block Slack notifications from 22:00 to 07:00"}

User: "Block emails containing the word 'offer'"
Output:
{"action":"block","app":null,"matchField":"any","matchType":"contains","matchPattern":"offer","isInverted":false,"timeStart":null,"timeEnd":null,"summary":"Block notifications containing 'offer'"}
```

---

## 4. `ParseCommandUseCase.kt` Design and Implementation

### Core Responsibilities
1. **Extraction**: Strip out any conversational filler or markdown wraps (like ````json ... ````) from the model's raw string output.
2. **Parsing**: Parse the JSON string using `org.json.JSONObject` (standard Android dependency, avoiding extra libraries).
3. **Enum Validation**: Ensure text values for `action`, `matchField`, and `matchType` correspond to the domain-level enums (`RuleAction`, `MatchField`, `MatchType`), falling back to sensible defaults on failure.
4. **App Package Resolution**: Check if the app parameter matches an installed application.
5. **Time Parsing**: Convert `"HH:mm"` string values into Java `LocalTime`.

### Package Visibility Warning (`AndroidManifest.xml` requirement)
On Android 11+ (API 30+), package visibility restrictions prevent apps from seeing other installed applications by default. Querying package info will always throw `NameNotFoundException`.
To solve this, we must declare a `<queries>` block in `app/src/main/AndroidManifest.xml` to allow the app to query launcher activities:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

### Proposed Code for `ParseCommandUseCase.kt`
Create `app/src/main/java/com/hush/app/domain/usecase/ParseCommandUseCase.kt`:

```kotlin
package com.hush.app.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AIEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.time.LocalTime
import javax.inject.Inject

class ParseCommandUseCase @Inject constructor(
    private val aiEngine: AIEngine,
    @ApplicationContext private val context: Context
) {

    suspend operator fun invoke(prompt: String): ParsedCommand {
        // 1. Get raw string response from AI engine
        val rawResult = aiEngine.parseCommand(prompt) // Assuming AIEngine is updated or returns string
        // Note: If AIEngine already returns ParsedCommand, parsing happens in AIEngineImpl, 
        // and ParseCommandUseCase does the verification, package name resolution, and validation.
        
        return rawResult
    }

    fun parseAndValidateJson(rawJson: String): ParsedCommand {
        try {
            val cleanJson = extractJson(rawJson)
            val json = JSONObject(cleanJson)

            // Validate Action
            val actionStr = json.optString("action", "allow").uppercase()
            val action = try { RuleAction.valueOf(actionStr) } catch (e: Exception) { RuleAction.ALLOW }

            // Validate App & Resolve Package Name
            val rawApp = json.optString("app", "").takeIf { it.isNotEmpty() && it != "null" }
            val resolvedApp = rawApp?.let { resolvePackageName(it) }

            // Validate Match Field
            val matchFieldStr = json.optString("matchField", "any").uppercase()
            val matchField = try { MatchField.valueOf(matchFieldStr) } catch (e: Exception) { MatchField.ANY }

            // Validate Match Type
            val matchTypeStr = json.optString("matchType", "contains").uppercase()
            val matchType = try { MatchType.valueOf(matchTypeStr) } catch (e: Exception) { MatchType.CONTAINS }

            // Validate Match Pattern
            val matchPattern = json.optString("matchPattern", "").takeIf { it.isNotEmpty() && it != "null" }
            val isInverted = json.optBoolean("isInverted", false)

            // Parse Time Window
            val timeStartStr = json.optString("timeStart", "").takeIf { it.isNotEmpty() && it != "null" }
            val timeStart = timeStartStr?.let { try { LocalTime.parse(it) } catch (e: Exception) { null } }

            val timeEndStr = json.optString("timeEnd", "").takeIf { it.isNotEmpty() && it != "null" }
            val timeEnd = timeEndStr?.let { try { LocalTime.parse(it) } catch (e: Exception) { null } }

            val summary = json.optString("summary", "Custom Rule")

            return ParsedCommand(
                action = action,
                app = resolvedApp,
                matchField = matchField,
                matchType = matchType,
                matchPattern = matchPattern,
                isInverted = isInverted,
                timeStart = timeStart,
                timeEnd = timeEnd,
                summary = summary
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed JSON received from AI Engine: ${e.message}", e)
        }
    }

    private fun extractJson(rawOutput: String): String {
        val start = rawOutput.indexOf('{')
        val end = rawOutput.lastIndexOf('}')
        if (start == -1 || end == -1 || start >= end) {
            throw IllegalArgumentException("No valid JSON object found in response.")
        }
        return rawOutput.substring(start, end + 1)
    }

    private fun resolvePackageName(appName: String): String {
        val pm = context.packageManager
        
        // If appName is already structured like a package name, return it directly
        if (appName.contains(".")) {
            return appName
        }

        // Query installed applications (requires <queries> block in AndroidManifest.xml)
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val matchedApp = installedApps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase()
            label == appName.lowercase()
        } ?: installedApps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase()
            label.contains(appName.lowercase())
        } ?: installedApps.firstOrNull { app ->
            app.packageName.lowercase().contains(appName.lowercase())
        }

        if (matchedApp != null) {
            return matchedApp.packageName
        }

        // Static fallback mapping for popular apps in case they are not installed
        return when (appName.lowercase()) {
            "whatsapp" -> "com.whatsapp"
            "slack" -> "com.slack"
            "instagram" -> "com.instagram.android"
            "gmail" -> "com.google.android.gm"
            "messenger" -> "com.facebook.orca"
            "telegram" -> "org.telegram.messenger"
            else -> "com.$appName.uninstalled" // structured placeholder to trigger warning in UI
        }
    }
}
```

---

## 5. Test Suite Verification and Layout

We verified that the test suite compiles and runs successfully using local and Android components.

### Test Structure
- **Unit Tests**: Co-located in `app/src/test/java/com/hush/app/`. They test business logic like `EvaluateNotificationUseCaseTest.kt` using JUnit 4.
- **Instrumented (E2E) Tests**: Placed under `app/src/androidTest/java/com/hush/app/e2e/`. They test full-stack components on an emulator/device.
  - `AppFoundationE2ETest.kt`: Validates onboarding flows, settings persistence, theme toggles, and Hilt injections.
  - `ConversationalAIE2ETest.kt`: Tests AI chat integration, voice recording transcription, malformed outputs, and database persistence.
  - `CrossFeatureE2ETest.kt` & `RealWorldScenarioE2ETest.kt`: Exercised end-to-end integration and real-world multi-rule priorities.

The test infrastructure is highly decoupling implementation from expectations by utilizing Hilt testing injections:
- `FakeAIEngine` is injected in unit/instrumented tests to mock responses.
- `FakeSpeechRecognizerWrapper` is used to simulate transcription.
- `FakePermissionManager` simulates the permissions framework.
