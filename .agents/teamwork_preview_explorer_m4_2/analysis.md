# AI Integration Analysis Report (Explorer 2)

This report details the architectural design and API specifications for integrating Gemini Nano via the Google Play Services Generative AI SDK (AICore) in the Hush application.

---

## 1. Google Play Services Generative AI SDK API Specifications

The Hush application utilizes on-device AI running locally. To avoid requiring external API keys and cloud communication, it uses the Google Play Services Generative AI SDK.

### 1.1 Gradle Configuration
To integrate the SDK, the following dependencies must be added:
*   **Version Catalog (`gradle/libs.versions.toml`)**:
    ```toml
    [versions]
    play-services-generativeai = "16.0.0-beta01" # Or latest stable version
    kotlinx-coroutines-play-services = "1.8.1"
    
    [libraries]
    play-services-generativeai = { group = "com.google.android.gms", name = "play-services-generativeai", version.ref = "play-services-generativeai" }
    kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "kotlinx-coroutines-play-services" }
    ```
*   **App Level Gradle (`app/build.gradle.kts`)**:
    ```kotlin
    dependencies {
        implementation(libs.play-services-generativeai)
        implementation(libs.kotlinx-coroutines.play.services) // For Task.await() support
    }
    ```

### 1.2 SDK Core Classes
The following classes are imported from the SDK:
```kotlin
import com.google.android.gms.generativeai.GenerativeModel
import com.google.android.gms.generativeai.GenerationConfig
import com.google.android.gms.generativeai.GenerativeModelClient
```

### 1.3 Model Configuration & Generation Options
To configure the model to parse natural language into a strict JSON output matching our domain rules:
1.  **`GenerationConfig`**: Defines temperature and format constraints. We set temperature to `0.0f` to enforce deterministic parsing, and response MIME type to `"application/json"` to restrict output structure.
    ```kotlin
    val config = GenerationConfig.builder()
        .setTemperature(0.0f) // Deterministic output
        .setResponseMimeType("application/json") // Enforce valid JSON structure
        .build()
    ```
2.  **`GenerativeModel`**: Built using context, model name, instructions, and configurations.
    ```kotlin
    val generativeModel = GenerativeModel.Builder(context)
        .setModelName("gemini-nano") // Target Gemini Nano on-device engine
        .setGenerationConfig(config)
        .setSystemInstruction(systemInstruction) // Core instructions for parsing and schema
        .build()
    ```

### 1.4 Generating Responses
To generate content asynchronously inside a suspending function:
```kotlin
import kotlinx.coroutines.tasks.await

suspend fun generateResponse(prompt: String): String {
    val response = generativeModel.generateContent(prompt).await()
    return response.text ?: throw IllegalStateException("Model returned empty response")
}
```

---

## 2. AICore Availability Checking in `AIEngineImpl.kt`

### 2.1 API Characteristics
The SDK checks AICore availability using:
```kotlin
val modelClient = GenerativeModelClient.getClient(context)
val task: Task<Boolean> = modelClient.isAvailable()
```
*   **Return Type**: Returns a `com.google.android.gms.tasks.Task<Boolean>` object. It does **not** natively return a Kotlin `Flow` or suspending call.
*   **Exceptions Thrown**:
    *   `com.google.android.gms.common.api.ApiException`: Thrown if Google Play Services is missing, disabled, or outdated.
    *   `java.lang.UnsupportedOperationException`: Thrown if the device hardware is incompatible (e.g. lacks required RAM/NPU capabilities).
    *   `java.lang.IllegalStateException`: Thrown if GMS Core is in an invalid state or fails to bind to the AICore service.

### 2.2 Thread-Safe Synchronous Caching Strategy
The `AIEngine` interface defines `isAvailable(): Boolean` as a synchronous method. Calling GMS IPC calls or blocking the calling thread inside a Compose UI rendering scope (such as `ChatScreen`'s `enabled = aiEngine.isAvailable()`) will block the main UI thread and cause Application Not Responding (ANR) issues.

**Proposed Architecture**:
We recommend checking the status asynchronously on initialization, caching the result in a thread-safe property, and falling back gracefully to `false` if any exceptions occur.

```kotlin
package com.hush.app.data.repository

import android.content.Context
import android.util.Log
import com.google.android.gms.generativeai.GenerativeModelClient
import com.hush.app.domain.repository.AIEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AIEngine {

    @Volatile
    private var isAvailableCached: Boolean = false

    init {
        // Run check asynchronously on Dispatchers.Default to avoid blocking UI during app init
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val modelClient = GenerativeModelClient.getClient(context)
                isAvailableCached = modelClient.isAvailable().await()
            } catch (e: Exception) {
                Log.e("AIEngineImpl", "Failed to check AICore availability", e)
                isAvailableCached = false
            }
        }
    }

    override fun isAvailable(): Boolean = isAvailableCached
    
    // ... parseCommand implementation
}
```

---

## 3. Dynamic Prompt Design and Package Resolution

To ensure Gemini Nano correctly resolves spoken/written application names to real package names (e.g. mapping "gmail" to `"com.google.android.gm"`), the list of installed applications should be queried and prepended dynamically to the prompt context.

### 3.1 Android Package Visibility Restrictions
Android 11 (API 30) and higher limits visibility of other apps installed on a device. To allow Hush to query package metadata:
1.  **AndroidManifest.xml Update**: We must add the `<uses-permission>` tag. Since Hush evaluates notifications from all installed applications, requesting `QUERY_ALL_PACKAGES` is the correct, approved category:
    ```xml
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
    ```

### 3.2 Querying Installed Packages in Kotlin
We construct a mapping of app names to package names using the `PackageManager`:
```kotlin
fun getInstalledApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val packages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
    } else {
        pm.getInstalledPackages(0)
    }
    return packages.mapNotNull { packageInfo ->
        val appLabel = packageInfo.applicationInfo?.loadLabel(pm)?.toString()
        val packageName = packageInfo.packageName
        if (appLabel != null && packageName != null) {
            appLabel to packageName
        } else {
            null
        }
    }
}
```

### 3.3 Prompt Template Structure

#### 3.3.1 System Instruction
```text
You are an AI assistant for the "Hush" Android app. Your task is to parse natural language commands into a structured JSON rule for notification filtering.

You MUST output a single, valid JSON object matching the schema below.
Do NOT output any markdown tags (like ```json), explanations, or extra text. Your response must be parseable by standard JSON parsers.

JSON Schema:
{
  "action": "block" | "allow" | "mute",
  "app": "package.name" | null,
  "matchField": "title" | "text" | "sender" | "any",
  "matchType": "contains" | "regex" | "exact",
  "matchPattern": "string" | null,
  "isInverted": boolean,
  "timeStart": "HH:mm" | null,
  "timeEnd": "HH:mm" | null,
  "summary": "human-readable description"
}

Rules for fields:
- "action": Must be "block", "allow", or "mute". If user says stop, block, silence, select "block" or "mute". If allow or let through, select "allow".
- "app": If the user specifies an app name, map it to its Package Name using the list of installed applications provided. If it's not in the list but they named an app, construct a fallback package name in format com.<appname>.uninstalled. If they didn't specify any app, set to null.
- "matchField": "title" (if matching title/sender name specifically), "text" (if matching message body/text), "sender" (if matching sender name), or "any" (default).
- "matchType": "contains" (default), "exact", or "regex".
- "matchPattern": The specific keyword, phrase, or sender name to match. If matching the entire app notifications without any keyword, set to null.
- "isInverted": Set to true if the rule says "block all except X" or similar. Otherwise false.
- "timeStart" and "timeEnd": In "HH:mm" format (24-hour) if a time range is specified. Otherwise null.
- "summary": A clear, concise English description of the rule (e.g. "Mute Telegram notifications containing 'crypto'").
```

#### 3.3.2 User Prompt Format
```text
INSTALLED APPLICATIONS ON DEVICE:
- App Name: "WhatsApp", Package Name: "com.whatsapp"
- App Name: "Gmail", Package Name: "com.google.android.gm"
- App Name: "Slack", Package Name: "com.Slack"

USER COMMAND:
"Block slack notifications containing promo code"
```

---

## 4. Proposed `AIEngineImpl.kt` Implementation

Below is the proposed design for the full implementation of `AIEngineImpl.kt` including prompt building, execution, and JSON parsing.

```kotlin
package com.hush.app.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.generativeai.GenerationConfig
import com.google.android.gms.generativeai.GenerativeModel
import com.google.android.gms.generativeai.GenerativeModelClient
import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.MatchType
import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AIEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AIEngine {

    @Volatile
    private var isAvailableCached: Boolean = false

    private val systemInstructions = """
        You are an AI assistant for the "Hush" Android app. Your task is to parse natural language commands into a structured JSON rule for notification filtering.
        You MUST output a single, valid JSON object matching the schema below. Do NOT output any markdown tags (like ```json), explanations, or extra text.
        
        JSON Schema:
        {
          "action": "block" | "allow" | "mute",
          "app": "package.name" | null,
          "matchField": "title" | "text" | "sender" | "any",
          "matchType": "contains" | "regex" | "exact",
          "matchPattern": "string" | null,
          "isInverted": boolean,
          "timeStart": "HH:mm" | null,
          "timeEnd": "HH:mm" | null,
          "summary": "human-readable description"
        }
    """.trimIndent()

    init {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val modelClient = GenerativeModelClient.getClient(context)
                isAvailableCached = modelClient.isAvailable().await()
            } catch (e: Exception) {
                Log.e("AIEngineImpl", "Failed to check AICore availability", e)
                isAvailableCached = false
            }
        }
    }

    override fun isAvailable(): Boolean = isAvailableCached

    override suspend fun parseCommand(prompt: String): ParsedCommand {
        if (!isAvailable()) {
            throw IllegalStateException("AICore unavailable")
        }

        // 1. Retrieve the installed applications on device
        val appListString = getInstalledAppsString()

        // 2. Build final prompt with dynamic installed app mappings
        val userPrompt = """
            INSTALLED APPLICATIONS ON DEVICE:
            ${'$'}appListString
            
            USER COMMAND:
            "${'$'}prompt"
        """.trimIndent()

        // 3. Configure generation
        val config = GenerationConfig.builder()
            .setTemperature(0.0f)
            .setResponseMimeType("application/json")
            .build()

        val model = GenerativeModel.Builder(context)
            .setModelName("gemini-nano")
            .setGenerationConfig(config)
            .setSystemInstruction(systemInstructions)
            .build()

        try {
            // 4. Generate content
            val response = model.generateContent(userPrompt).await()
            val responseText = response.text ?: throw IllegalStateException("Model returned empty text")

            // 5. Parse JSON response
            val json = JSONObject(responseText)
            
            // Map action string to RuleAction Enum
            val action = when (json.getString("action").lowercase()) {
                "allow" -> RuleAction.ALLOW
                "block" -> RuleAction.BLOCK
                "mute" -> RuleAction.MUTE
                else -> RuleAction.MUTE
            }

            // Map matchField string to MatchField Enum
            val matchField = when (json.getString("matchField").lowercase()) {
                "title" -> MatchField.TITLE
                "text" -> MatchField.TEXT
                "sender" -> MatchField.SENDER
                "any" -> MatchField.ANY
                else -> MatchField.ANY
            }

            // Map matchType string to MatchType Enum
            val matchType = when (json.getString("matchType").lowercase()) {
                "contains" -> MatchType.CONTAINS
                "regex" -> MatchType.REGEX
                "exact" -> MatchType.EXACT
                else -> MatchType.CONTAINS
            }

            val app = if (json.isNull("app")) null else json.getString("app")
            val matchPattern = if (json.isNull("matchPattern")) null else json.getString("matchPattern")
            val isInverted = json.optBoolean("isInverted", false)

            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            val timeStartStr = if (json.isNull("timeStart")) null else json.getString("timeStart")
            val timeEndStr = if (json.isNull("timeEnd")) null else json.getString("timeEnd")

            val timeStart = timeStartStr?.let { LocalTime.parse(it, timeFormatter) }
            val timeEnd = timeEndStr?.let { LocalTime.parse(it, timeFormatter) }
            val summary = json.getString("summary")

            return ParsedCommand(
                action = action,
                app = app,
                matchField = matchField,
                matchType = matchType,
                matchPattern = matchPattern,
                isInverted = isInverted,
                timeStart = timeStart,
                timeEnd = timeEnd,
                summary = summary
            )
        } catch (e: Exception) {
            Log.e("AIEngineImpl", "Failed to parse command response", e)
            throw IllegalArgumentException("Failed to parse command: ${e.message}", e)
        }
    }

    private fun getInstalledAppsString(): String {
        val pm = context.packageManager
        val packages = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getInstalledPackages(0)
            }
        } catch (e: Exception) {
            emptyList()
        }

        return packages.mapNotNull { packageInfo ->
            val label = packageInfo.applicationInfo?.loadLabel(pm)?.toString()
            val name = packageInfo.packageName
            if (label != null && name != null) {
                "- App Name: \"$label\", Package Name: \"$name\""
            } else null
        }.joinToString("\n")
    }
}
```
