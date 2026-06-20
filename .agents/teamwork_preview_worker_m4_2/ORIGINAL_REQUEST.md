## 2026-06-20T17:40:05Z
You are Worker 2 for Milestone 4 (AI Integration). You are replacing Worker 1 who went unresponsive.
Your task is to implement the following changes in the Hush project codebase.

MANDATORY INTEGRITY WARNING — you MUST follow this instruction strictly:
"DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected."

### Required Implementation Subtasks:
1. **Google Play Services Generative AI SDK Dependency Setup**:
   - In `gradle/libs.versions.toml`:
     - Add `play-services-generativeai = "16.0.0-beta01"` under `[versions]`.
     - Add `kotlinx-coroutines-play-services = "1.8.1"` under `[versions]`.
     - Add `play-services-generativeai = { group = "com.google.android.gms", name = "play-services-generativeai", version.ref = "play-services-generativeai" }` under `[libraries]`.
     - Add `kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "kotlinx-coroutines-play-services" }` under `[libraries]`.
   - In `app/build.gradle.kts`:
     - Add `implementation(libs.play-services-generativeai)` and `implementation(libs.kotlinx.coroutines.play.services)` under `dependencies {}`.

2. **Package Visibility Configuration**:
   - In `app/src/main/AndroidManifest.xml`, add the `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` permission.

3. **PackageResolver & PackageResolverImpl**:
   - Create `app/src/main/java/com/hush/app/domain/repository/PackageResolver.kt`:
     ```kotlin
     package com.hush.app.domain.repository
     
     interface PackageResolver {
         fun getInstalledApps(): List<AppInfo>
         fun resolvePackage(appName: String): String?
         fun isInstalled(packageName: String): Boolean
     }
     
     data class AppInfo(
         val displayName: String,
         val packageName: String
     )
     ```
   - Create `app/src/main/java/com/hush/app/data/repository/PackageResolverImpl.kt`:
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
             // Exact Match
             apps.find { it.displayName.lowercase() == normalized }?.let { return it.packageName }
             // Substring Match
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

4. **Prompt Templates**:
   - Create `app/src/main/java/com/hush/app/data/repository/PromptTemplates.kt`:
     ```kotlin
     package com.hush.app.data.repository
     
     object PromptTemplates {
         val SYSTEM_INSTRUCTIONS = """
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
     }
     ```

5. **AIEngineImpl.kt**:
   - Re-implement `AIEngineImpl.kt` to:
     - Asynchronously check model client availability on initialization to avoid blocking the main UI thread. Cache the result in a volatile boolean.
     - In `parseCommand`, build the final prompt with dynamic app list mappings using the package resolver.
     - Call `GenerativeModel.Builder` to build model with temperature=0.0f, responseMimeType="application/json", and systemInstructions.
     - Generate content, parse model JSON output using `JSONObject`, and return `ParsedCommand`.
     - Handle parsing and SDK exceptions, throw `IllegalArgumentException` on parsing/sdk failure.

6. **ParseCommandUseCase.kt**:
   - Create `app/src/main/java/com/hush/app/domain/usecase/ParseCommandUseCase.kt` to validate the command, resolve friendly app names to packages, and check installation status.

7. **DI / Hilt Bindings**:
   - Update `app/src/main/java/com/hush/app/di/AIModule.kt` to bind `PackageResolver` to `PackageResolverImpl`.
   - Update `app/src/ui/screens/chat/ChatViewModel.kt` to inject `ParseCommandUseCase`.
   - Update `app/src/ui/screens/chat/ChatScreen.kt` to call `ParseCommandUseCase` in `handleSend`.

8. **Test & E2E Configurations**:
   - Update `app/src/androidTest/java/com/hush/app/mock/FakePackageResolver.kt` and `app/src/androidTest/java/com/hush/app/di/TestAIModule.kt` to inject and bind the fake.
   - Update `app/src/androidTest/java/com/hush/app/e2e/ConversationalAIE2ETest.kt` to seed the mock apps.
   - Write comprehensive unit tests for `ParseCommandUseCase` and `AIEngineImpl`.

Please perform these implementations, compile the app, and run the tests to verify everything passes. Document your changes and verification commands in your handoff report.
Your working directory is /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/teamwork_preview_worker_m4_2/.
