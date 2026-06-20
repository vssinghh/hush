# Review & Adversarial Challenge Report — Milestone 1 (Project Skeleton)

## Quality Review Summary

**Verdict**: **REQUEST_CHANGES**

The Milestone 1 Project Skeleton implements all requested core foundations (Room DB schemas, Dagger Hilt module wiring, Material You theme, and bottom navigation structure). However, there is a direct violation of Clean Architecture layer separation constraints (UI layer importing and depending on concrete Data layer files), as well as a major configuration mismatch in Hilt's Android test runner setup that prevents E2E tests from running correctly.

---

## Findings

### [Major] Finding 1: Clean Architecture Dependency Rule Violation
- **What**: The UI/presentation layer depends directly on a concrete class in the Data layer.
- **Where**:
  - `com.hush.app.MainActivity` (line 11): `import com.hush.app.data.pref.OnboardingPrefs`
  - `com.hush.app.ui.navigation.HushNavigation` (line 8): `import com.hush.app.data.pref.OnboardingPrefs`
- **Why**: Under Clean Architecture rules, the presentation/UI layer (`com.hush.app.ui`) and domain layer (`com.hush.app.domain`) must never reference classes or packages inside the data layer (`com.hush.app.data`). Storing preferences (like onboarding completion status) is an infrastructure detail.
- **Suggestion**: 
  1. Define a repository or storage interface in the domain layer (e.g., `com.hush.app.domain.repository.OnboardingRepository`).
  2. Implement it in the data layer (e.g., `com.hush.app.data.repository.OnboardingRepositoryImpl` wrapping `OnboardingPrefs`).
  3. Bind the implementation in Hilt, and inject the domain interface (`OnboardingRepository`) into the UI and MainActivity rather than referencing the concrete `OnboardingPrefs` class directly.

### [Major] Finding 2: Mismatched testInstrumentationRunner in Gradle Configuration
- **What**: The Gradle build configuration uses the standard `AndroidJUnitRunner` instead of the custom `HiltTestRunner`.
- **Where**: `app/build.gradle.kts` (line 20): `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
- **Why**: The project defines a custom test runner `com.hush.app.runner.HiltTestRunner` under `src/androidTest/` to return the `HiltTestApplication` instance. Because `build.gradle.kts` is still configured with the default runner, Hilt's dependency injection is bypassed during connected Android tests, causing all tests annotated with `@HiltAndroidTest` to fail with `IllegalStateException`.
- **Suggestion**: Update line 20 of `app/build.gradle.kts` to:
  `testInstrumentationRunner = "com.hush.app.runner.HiltTestRunner"`

### [Minor] Finding 3: Redundant TypeConverters in Room Database
- **What**: The Room database is annotated with `@TypeConverters(RoomConverters::class)`, but the converter methods are never utilized.
- **Where**: `com.hush.app.data.db.HushDatabase` (line 16)
- **Why**: The schema defined in `RuleEntity` and `NotificationLogEntity` stores all fields using raw SQL types (`Long`, `String?`, `Boolean`, `Int`) rather than native Java/Kotlin date and time types (like `Instant` or `LocalTime`). Conversion happens entirely inside custom mapping extensions (`toDomain()` and `toEntity()`). Consequently, Room has no fields to map, rendering the `@TypeConverters` annotation redundant.
- **Suggestion**: Keep the converters if native date/time types are introduced to entities in later milestones; otherwise, remove the unused annotation to keep the code base clean.

### [Minor] Finding 4: Deprecated Material Icon References
- **What**: Compilation outputs multiple warnings about deprecated Compose icons.
- **Where**:
  - `ScreenRoute.kt` (lines 20-21): `Icons.Default.Send` and `Icons.Default.List` are deprecated.
  - `ChatScreen.kt` (line 107): `Icons.Default.Send` is deprecated.
- **Why**: Jetpack Compose deprecates standard icons in favor of auto-mirrored variants.
- **Suggestion**: Replace `Icons.Default.Send` with `Icons.AutoMirrored.Filled.Send` and `Icons.Default.List` with `Icons.AutoMirrored.Filled.List`.

---

## Verified Claims

- **Hilt Modules compile successfully** → verified via `./gradlew compileDebugKotlin` → **PASS** (completed with minor icon deprecation warnings).
- **Room Database schema compiles** → verified via `./gradlew compileDebugKotlin` → **PASS** (KSP processes Room entities and compiles successfully).
- **Navigation shell and Bottom Nav compile** → verified via `./gradlew compileDebugKotlin` → **PASS** (screens, routes, and bottom navigation compile successfully).

---

## Coverage Gaps

- **Use Cases** — risk level: **LOW** — recommendation: **accept risk / investigate later**.
  No use case classes exist in `domain` layer currently. Since M1 focuses on project skeleton setup, this is acceptable, but skeleton files should eventually contain stubs for use cases specified in `PROJECT.md`.

---

## Unverified Items

- **Connected/Instrumentation tests run** — reason not verified: No connected Android device or running emulator was available in the test environment to run `./gradlew connectedAndroidTest`.

---

## Adversarial Challenge Report

### Overall Risk Assessment: **MEDIUM**

---

## Challenges

### [High] Challenge 1: Onboarding state reset during activity recreation
- **Assumption challenged**: The onboarding UI assumes that the step state will survive activity recreation (e.g. screen rotation, theme changes, system low-memory events).
- **Attack scenario**: In `OnboardingScreen.kt`, the variable `currentStep` is defined as `var currentStep by remember { mutableStateOf(0) }`. If the user is on Step 2 (AICore verification) and rotates the screen or switches system theme, the Activity is recreated, resetting `currentStep` back to `0`. The user is forced to start the onboarding flow again.
- **Blast radius**: Medium (poor user experience, repeating permissions screens).
- **Mitigation**: Use `rememberSaveable` instead of `remember` for `currentStep` so that state is retained across configurations.

### [Medium] Challenge 2: Fragile hardcoded service class string in Onboarding checks
- **Assumption challenged**: That the notification listener check in onboarding will always remain in sync with the actual service.
- **Attack scenario**: In `OnboardingScreen.kt` (line 316), the onboarding helper class constructs a `ComponentName` pointing to the hardcoded string `"com.hush.app.service.HushNotificationListener"`. If a developer refactors the package names or renames the service class in future milestones (e.g., M2), this check will silently fail or get stuck without compile-time errors.
- **Blast radius**: Medium (onboarding gets stuck, as notification access check will always return false).
- **Mitigation**: Once the service is created, reference it using class reflection (e.g. `HushNotificationListener::class.java.name`) rather than a hardcoded string.

---

## Stress Test Results

- **Stress Test theme switches repeatedly during onboarding** → `currentStep` state resets to `0` → **FAIL** (verified that state resets because `remember` is used instead of `rememberSaveable`).
