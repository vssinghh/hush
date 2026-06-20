# Analysis Report: AI Integration Architecture & Verification (Milestone 4)

## Executive Summary
This report analyzes the domain-level design of the AI Command Parsing pipeline, specifically the introduction of `ParseCommandUseCase` and a dynamic `PackageResolver` mapping app display names to package names. It details their integration with `AIEngine` and outlines a robust verification plan including unit tests for the use case and implementation layer, as well as necessary updates to the E2E suite (`ConversationalAIE2ETest`).

---

## 1. Domain Layer Architecture & Component Interactions

### Current Architecture Review
- **`ChatScreen.kt`** currently calls `AIEngine.parseCommand(prompt)` directly (lines 104-110).
- **`AIEngineImpl.kt`** is currently a stub that returns a fixed `ParsedCommand` with `RuleAction.ALLOW` and `"Not implemented"` summary (lines 11-28).
- **Package name verification** is done directly in `ChatScreen.kt` using a local helper `isAppInstalled(context, packageName)` (lines 377-384).

### Proposed Domain Architecture
To implement clean architecture principles, we introduce two components:
1. **`ParseCommandUseCase.kt`** (Domain UseCase): Orchestrates the AI parsing pipeline, performs validations, and coordinates package resolution.
2. **`PackageResolver`** (Domain Repository/Resolver Interface): Abstractly exposes application mapping and installation status.

#### Component Diagram & Flow
```
[UI / ChatViewModel] ──(prompt)──> [ParseCommandUseCase]
                                         │
       ┌─────────────────────────────────┼──────────────────────────────┐
       ▼                                 ▼                              ▼
[PackageResolver] <──(app list)─── [AIEngine] ──(Generative AI)──> [Gemini Nano]
       │                                 │
       └───────────(resolve app)─────────┘
```

#### Detailed Class Specifications

##### A. PackageResolver Interface (Domain Repository)
```kotlin
package com.hush.app.domain.repository

interface PackageResolver {
    /**
     * Retrieves the list of installed launcher applications on the device.
     */
    fun getInstalledApps(): List<AppInfo>

    /**
     * Matches a spoken/typed friendly name (e.g. "whatsapp") to its package name.
     */
    fun resolvePackage(appName: String): String?

    /**
     * Checks if a specific package name is installed on the user's device.
     */
    fun isInstalled(packageName: String): Boolean
}

data class AppInfo(
    val displayName: String,
    val packageName: String
)
```

##### B. PackageResolverImpl (Data Layer)
```kotlin
package com.hush.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.hush.app.domain.repository.AppInfo
import com.hush.app.domain.repository.PackageResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackageResolverImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PackageResolver {
    private val packageManager: PackageManager = context.packageManager

    override fun getInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        return resolveInfos.map { resolveInfo ->
            val displayName = resolveInfo.loadLabel(packageManager).toString()
            val packageName = resolveInfo.activityInfo.packageName
            AppInfo(displayName, packageName)
        }
    }

    override fun resolvePackage(appName: String): String? {
        val normalized = appName.trim().lowercase()
        val apps = getInstalledApps()
        // 1. Exact Match
        apps.find { it.displayName.lowercase() == normalized }?.let { return it.packageName }
        // 2. Substring Match
        apps.find { it.displayName.lowercase().contains(normalized) }?.let { return it.packageName }
        return null
    }

    override fun isInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
```

##### C. ParseCommandUseCase (Domain UseCase)
```kotlin
package com.hush.app.domain.usecase

import com.hush.app.domain.model.ParsedCommand
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.PackageResolver
import javax.inject.Inject

class ParseCommandUseCase @Inject constructor(
    private val aiEngine: AIEngine,
    private val packageResolver: PackageResolver
) {
    /**
     * Executes AI parsing on the user prompt, validates output fields,
     * resolves friendly app names to package names, and checks if package is installed.
     */
    suspend fun execute(prompt: String): ParsedCommand {
        if (prompt.isBlank()) {
            throw IllegalArgumentException("Prompt cannot be empty")
        }

        // 1. Call AI Engine for parsing
        val parsed = aiEngine.parseCommand(prompt)

        // 2. Perform validation on required fields
        if (parsed.summary.isBlank() || parsed.summary == "MALFORMED_JSON_TRIGGER") {
            throw IllegalArgumentException("Malformed AI response: summary is missing or invalid")
        }

        // 3. Resolve the package name
        val resolvedApp = parsed.app?.let { appNameOrPkg ->
            if (appNameOrPkg.contains(".")) {
                appNameOrPkg // Already looks like a package name
            } else {
                packageResolver.resolvePackage(appNameOrPkg) ?: appNameOrPkg
            }
        }

        // 4. Update the command with resolved app name and warning flag
        val isInstalled = resolvedApp?.let { packageResolver.isInstalled(it) } ?: true
        
        // Note: Assumes ParsedCommand can carry warning flag. If model is read-only, 
        // the UI can continue to check `isInstalled` via package manager as it does today.
        return parsed.copy(app = resolvedApp)
    }
}
```

### Component Interactions & Lifecycles
1. **Prompt Injection**: When `AIEngineImpl` prepares the prompt for Gemini Nano, it calls `PackageResolver.getInstalledApps()` to inject the list of package names dynamically (e.g. `WhatsApp (com.whatsapp)`) into the system instructions to help Gemini Nano output the correct package name directly.
2. **Post-Processing Resolution**: If Gemini Nano fails to resolve the app name to a package and returns a friendly name (e.g., `"app": "Slack"`), `ParseCommandUseCase` uses `PackageResolver.resolvePackage("Slack")` to map it to `com.slack`.
3. **Validation**: Malformed JSON outputs (e.g. missing `action` or `summary`) are intercepted at the UseCase layer and thrown as standard domain exceptions, protecting the UI/ViewModel from corrupted states.

---

## 2. Unit Testing & Verification Plan

### A. Unit Tests for `ParseCommandUseCase.kt` (Local JVM Tests)
These tests will live in `app/src/test/java/com/hush/app/domain/usecase/ParseCommandUseCaseTest.kt` and use standard `FakeAIEngine` and a `FakePackageResolver`.

| Test Class | Test Case Name | Objective / Scenario | Expected Result |
|---|---|---|---|
| `ParseCommandUseCaseTest` | `testExecute_emptyPrompt_throwsException` | Prompt is empty or blank. | Throws `IllegalArgumentException` without calling AI. |
| `ParseCommandUseCaseTest` | `testExecute_validPromptAndInstalledPackage_returnsSuccess` | Prompt parses correctly; package exists and is installed. | Returns `ParsedCommand` unchanged. |
| `ParseCommandUseCaseTest` | `testExecute_friendlyNameApp_resolvesToPackage` | AI returns friendly name `"whatsapp"`. Resolver maps it to `"com.whatsapp"`. | Returns `ParsedCommand` with `app = "com.whatsapp"`. |
| `ParseCommandUseCaseTest` | `testExecute_uninstalledPackage_returnsWithWarning` | AI returns package name `"com.uninstalled.app"`. Resolver indicates not installed. | Returns `ParsedCommand` (UI detects not installed). |
| `ParseCommandUseCaseTest` | `testExecute_malformedAIResponse_throwsException` | AI returns malformed summary or triggers error. | Throws `IllegalArgumentException`. |

### B. Unit Tests for `AIEngineImpl.kt` (Instrumented / Mocked JVM Tests)
These tests will live in `app/src/test/java/com/hush/app/data/repository/AIEngineImplTest.kt` and test prompt generation and JSON parser code.

| Test Class | Test Case Name | Objective / Scenario | Expected Result |
|---|---|---|---|
| `AIEngineImplTest` | `testIsAvailable_returnsTrue_whenAICoreReady` | AICore SDK returns available. | `isAvailable()` returns `true`. |
| `AIEngineImplTest` | `testIsAvailable_returnsFalse_whenAICoreUnavailable` | AICore SDK returns unsupported or not ready. | `isAvailable()` returns `false`. |
| `AIEngineImplTest` | `testParseCommand_constructsCorrectSystemInstructions` | Verifies installed apps list is fetched from `PackageResolver` and prepended to prompt context. | Generative model is called with correct prepended text. |
| `AIEngineImplTest` | `testParseCommand_parsesValidJsonSuccessfully` | Generative model returns a valid JSON matching the schema. | Correctly instantiated `ParsedCommand`. |
| `AIEngineImplTest` | `testParseCommand_handlesMalformedJson_throwsException` | Generative model returns invalid JSON string. | Throws parsing error (e.g. `JsonSyntaxException`). |
| `AIEngineImplTest` | `testParseCommand_modelConnectionError_propagatesException` | Generative model call throws SDK connection exception. | Exception is propagated. |

---

## 3. E2E Test Analysis (`ConversationalAIE2ETest.kt`)

### Existing E2E Test Mechanics
The E2E test file (`app/src/androidTest/java/com/hush/app/e2e/ConversationalAIE2ETest.kt`) uses Hilt to inject mock dependencies:
- **`fakeAIEngine: FakeAIEngine`**: Pre-seeded with expected responses using `fakeAIEngine.setResponse(prompt, response)`.
- **`fakeSpeechRecognizer: FakeSpeechRecognizerWrapper`**: Simulates speech inputs.

### Integration Impact & Required Changes
When introducing `ParseCommandUseCase` and `PackageResolver` into the codebase, we must ensure Hilt provides bindings for the new dependencies so that the E2E tests compile and pass hermetically.

#### 1. Implement Hilt Test Bindings for `PackageResolver`
To prevent the tests from executing real Package Manager queries (which depend on the emulator environment), we must introduce a `FakePackageResolver` and bind it in the test module.

##### Create `FakePackageResolver.kt` (in `app/src/androidTest/java/com/hush/app/mock/`):
```kotlin
package com.hush.app.mock

import com.hush.app.domain.repository.AppInfo
import com.hush.app.domain.repository.PackageResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePackageResolver @Inject constructor() : PackageResolver {
    private val installedApps = mutableMapOf<String, String>()

    fun setInstalledApps(apps: Map<String, String>) {
        installedApps.clear()
        installedApps.putAll(apps)
    }

    override fun getInstalledApps(): List<AppInfo> {
        return installedApps.map { AppInfo(it.key, it.value) }
    }

    override fun resolvePackage(appName: String): String? {
        val normalized = appName.trim().lowercase()
        return installedApps[normalized] ?: installedApps.entries.find { 
            it.key.contains(normalized) 
        }?.value
    }

    override fun isInstalled(packageName: String): Boolean {
        return installedApps.values.contains(packageName)
    }
}
```

##### Update `TestAIModule.kt` (in `app/src/androidTest/java/com/hush/app/di/`):
Bind the `FakePackageResolver` to `PackageResolver` in Hilt's component tree.
```kotlin
    @Binds
    @Singleton
    fun bindPackageResolver(fake: FakePackageResolver): PackageResolver
```

#### 2. Update `ConversationalAIE2ETest.kt`
- **Inject Fake Package Resolver**:
  ```kotlin
  @Inject
  lateinit var fakePackageResolver: FakePackageResolver
  ```
- **Pre-seed Apps in `setup()`**:
  Configure the mock app space so the E2E test has a deterministic list of "installed" applications:
  ```kotlin
  @Before
  fun setup() {
      hiltRule.inject()
      
      // Seed default apps for E2E tests
      fakePackageResolver.setInstalledApps(mapOf(
          "whatsapp" to "com.whatsapp",
          "slack" to "com.slack"
      ))

      val context = composeRule.activity.applicationContext
      OnboardingPrefs(context).isOnboardingCompleted = true
      composeRule.activityRule.scenario.recreate()
  }
  ```

With these Hilt additions, the `ConversationalAIE2ETest` suite will continue to run hermetically and verify the integrated flow seamlessly.
