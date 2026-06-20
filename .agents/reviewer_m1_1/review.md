# Milestone 1: Project Skeleton Review Report

## Review Summary

**Verdict**: REQUEST_CHANGES

While the core skeleton builds successfully, follows Clean Architecture, and integrates Dagger Hilt and Room DB properly, there are several major and minor issues that must be addressed (particularly the missing permission declarations in `AndroidManifest.xml` which will break runtime features).

---

## Findings

### [Major] Finding 1: Missing Permission Declarations in Manifest

- **What**: The app's `AndroidManifest.xml` does not declare the `android.permission.RECORD_AUDIO` permission.
- **Where**: `app/src/main/AndroidManifest.xml`
- **Why**: The onboarding flow (`OnboardingScreen.kt`) requests microphone permission via `micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)`. Without declaring it in the manifest, Android will immediately deny the permission request, preventing the microphone functionality from ever working.
- **Suggestion**: Add the following permission request inside the `<manifest>` tag:
  ```xml
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  ```
  Additionally, declare `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />` if battery exemption is requested.

### [Major] Finding 2: Unused Type Converters

- **What**: `RoomConverters` has converters registered for `Instant` and `LocalTime`, but neither is actually used by the Room entities.
- **Where**: `app/src/main/java/com/hush/app/data/db/entity/RuleEntity.kt` and `NotificationLogEntity.kt`
- **Why**: The entity classes declare their date/time fields as primitives (`Long` and `String` for timestamps and local times, respectively) and handle conversion manually in the mapper functions (`toEntity()` and `toDomain()`). Thus, Room does not use `RoomConverters`.
- **Suggestion**: Either change the entity definitions to use `Instant` and `LocalTime` directly (allowing Room to map them automatically via the registered converters) or remove the unused `RoomConverters` registration to prevent confusion.

### [Minor] Finding 3: Inconsistent Room Schema Export Settings

- **What**: `exportSchema` is set to `false` in `HushDatabase`, but `room.schemaLocation` is configured in Gradle.
- **Where**: `app/src/main/java/com/hush/app/data/db/HushDatabase.kt` (line 14) and `app/build.gradle.kts` (line 27)
- **Why**: Setting `exportSchema = false` prevents Room from exporting the database schema, rendering the Gradle KSP configuration `room.schemaLocation` useless.
- **Suggestion**: Set `exportSchema = true` so schemas are exported and can be committed to track database changes and schema migrations over time, or remove the Gradle configuration if schema tracking is not wanted.

### [Minor] Finding 4: Deprecated Compose Icons

- **What**: Deprecated icon properties `Icons.Filled.Send` and `Icons.Filled.List` are used in Screen routes and screens.
- **Where**: `app/src/main/java/com/hush/app/ui/navigation/ScreenRoute.kt` (lines 20-21) and `ChatScreen.kt` (line 107)
- **Why**: Standard `Send` and `List` icons are deprecated in recent Material Design Compose releases in favor of their AutoMirrored equivalents. This triggers Gradle compilation warnings.
- **Suggestion**: Replace `Icons.Default.Send` with `Icons.AutoMirrored.Filled.Send` and `Icons.Default.List` with `Icons.AutoMirrored.Filled.List`.

### [Minor] Finding 5: Redundant SDK Version Check for Dynamic Color

- **What**: Redundant Android version check for dynamic color.
- **Where**: `app/src/main/java/com/hush/app/ui/theme/Theme.kt` (line 40)
- **Why**: The conditional checks `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (Android 12/SDK 31), but `minSdk` of the application is set to `33` in `app/build.gradle.kts`. Hence, the SDK check will always evaluate to true.
- **Suggestion**: Remove `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` since the minSdk guarantees SDK 33+.

### [Minor] Finding 6: Hardcoded Package Name in ComponentName

- **What**: The package name is hardcoded in the `ComponentName` creation for checking notification listener access.
- **Where**: `app/src/main/java/com/hush/app/ui/screens/onboarding/OnboardingScreen.kt` (line 316)
- **Why**: Hardcoding the package name `"com.hush.app.service.HushNotificationListener"` is fragile. If the application ID changes in the future, the check will break.
- **Suggestion**: Use `context.packageName` dynamically:
  ```kotlin
  val cn = ComponentName(context.packageName, "com.hush.app.service.HushNotificationListener")
  ```

---

## Verified Claims

- **Gradle Build Compiles**: Verified using `./gradlew assembleDebug`. Result: **PASS** (completed successfully in 317ms).
- **Clean Architecture Directory Layout**: Verified package directories `ui`, `data`, `di`, and `domain`. Result: **PASS**.
- **Room Database Setup**: Verified entities (`RuleEntity`, `NotificationLogEntity`), DAOs (`RuleDao`, `NotificationLogDao`), and database (`HushDatabase`) presence and mappings. Result: **PASS** (tested write/read via in-memory database test).
- **Dagger Hilt Setup**: Verified app class `@HiltAndroidApp` and modules in `di/`. Result: **PASS**.
- **Dynamic Theming & Compose Navigation**: Checked Material 3 `HushTheme` dynamic color implementation and `HushNavigation` nested bottom bar setups. Result: **PASS**.

---

## Coverage Gaps

- **Missing Notification Listener Service**: The listener class `com.hush.app.service.HushNotificationListener` is referenced but does not exist in the source codebase.
  - *Risk Level*: Low (Planned for Milestone 2, so it is an expected gap for the project skeleton milestone).
  - *Recommendation*: Implement the service in Milestone 2 as planned.

- **Lack of Local Unit Tests**: `src/test/` folder is empty, and Gradle unit tests targets report `NO-SOURCE`.
  - *Risk Level*: Medium.
  - *Recommendation*: Write domain use-case unit tests to ensure business logic correctness.

---

## Unverified Items

- **Instrumented UI/Database Tests Execution**: Unable to run instrumented tests (`./gradlew connectedAndroidTest`) because no emulator or physical device is connected in the sandbox. Verified only compilation of test classes.

---

# Adversarial Challenge Report

## Challenge Summary

**Overall risk assessment**: MEDIUM

While the skeleton architecture is clean, several assumptions have been made that could fail in real-world scenarios or customized Android devices.

---

## Challenges

### [Medium] Challenge 1: Unhandled ActivityNotFoundException on Customized ROMs

- **Assumption challenged**: The system settings intents (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` and `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) are assumed to always exist and be launchable.
- **Attack scenario**: On highly restricted Android flavors (e.g. customized enterprise ROMs, Android TV, or custom OEM skins like MIUI/HyperOS), these specific settings intents may not be registered, causing `context.startActivity()` to throw an `ActivityNotFoundException`. This will crash the app instantly.
- **Blast radius**: App crash during the onboarding flow.
- **Mitigation**: Wrap the `startActivity()` calls in `try-catch` blocks and display a toast or fallback screen advising the user to configure the setting manually.

### [Low] Challenge 2: Fragile Foreign Key Integrity in Log History

- **Assumption challenged**: The relationship between `NotificationLogEntity` and `RuleEntity` uses a foreign key `matchedRuleId` with `onDelete = ForeignKey.SET_NULL`.
- **Attack scenario**: When a rule is updated or deleted, it is important to preserve historical audit context. Although the schema correctly sets the ID to null (preventing constraints violation), if the rule name changes or is deleted, looking at the logs alone would show a null rule ID.
- **Blast radius**: Historical records could lose context if we only relied on the rule ID.
- **Mitigation**: The implementer mitigates this by copying the rule name into `matchedRuleName: String?` at creation time, preserving context even if the rule is deleted. This is a robust design.

---

## Stress Test Results

- **Run build without ANDROID_HOME**: Evaluated compiler without environment variables. Result: **FAIL** (as expected, SDK path required).
- **Run build with JDK 26 vs JDK 17**: Checked if compiling under newer JDK causes issues. Result: **PASS** (compilation succeeded on both).

---

## Unchallenged Areas

- **Gemini Nano API / AICore Integration**: The implementation of `AIEngineImpl` is currently a placeholder returning false. Real-world challenges (such as model load timeouts, out-of-memory errors on low-ram devices, or API version mismatch) cannot be tested until Milestone 4.
