# Verification Report: Project Skeleton (Milestone 1)

## Challenge Summary

**Overall risk assessment**: **HIGH** (due to compile failure of the E2E/instrumented test suite, preventing any empirical testing of features)

---

## Key Findings & Critical Challenges

### [Critical] Challenge 1: Missing Hilt Testing Dependencies and Compilation Failure of Instrumented Tests
- **Assumption challenged**: The project skeleton is test-ready and the E2E test suite can be run on device/emulator.
- **Attack scenario / Symptom**: Running any task that compiles the instrumented tests (e.g. `./gradlew compileDebugAndroidTestSources` or `./gradlew connectedAndroidTest`) fails during KSP processing.
- **Root Cause**:
  1. `libs.versions.toml` lacks a definition for `hilt-android-testing`.
  2. `app/build.gradle.kts` lacks `androidTestImplementation(libs.hilt.android.testing)` and the KSP processor for test classes `kspAndroidTest(libs.hilt.compiler)`.
  3. Consequently, classes in `app/src/androidTest/` that import `dagger.hilt.testing.TestInstallIn` fail to compile. KSP resolves this annotation to `error.NonExistentClass` and errors out, stating:
     ```
     e: [ksp] .../TestAIModule.kt:18: [Hilt] com.hush.app.di.TestAIModule is missing an @InstallIn annotation.
     e: [ksp] ModuleProcessingStep was unable to process 'com.hush.app.di.TestAIModule' because 'error.NonExistentClass' could not be resolved.
     ```
- **Blast radius**: The entire suite of 51 E2E tests cannot be compiled or run.
- **Mitigation**: 
  - Add `hilt-android-testing = { group = "com.google.dagger", name = "hilt-android-testing", version.ref = "hilt" }` to `libs.versions.toml`.
  - Add `androidTestImplementation(libs.hilt.android.testing)` and `kspAndroidTest(libs.hilt.compiler)` to the `dependencies` block in `app/build.gradle.kts`.

### [High] Challenge 2: Misconfigured Test Runner
- **Assumption challenged**: The custom `HiltTestRunner` is used to bootstrap tests.
- **Attack scenario**: Even if dependencies are fixed and tests compile, running them under the default runner will result in injection failures, because Hilt tests require a Hilt-aware application class.
- **Root Cause**: In `app/build.gradle.kts`, the runner configuration is:
  ```kotlin
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  ```
  However, the project contains a custom test runner:
  ```kotlin
  package com.hush.app.runner
  class HiltTestRunner : AndroidJUnitRunner() { ... }
  ```
- **Blast radius**: Execution of `@HiltAndroidTest` annotated test classes will fail at runtime.
- **Mitigation**: Update `testInstrumentationRunner` in `app/build.gradle.kts` to `"com.hush.app.runner.HiltTestRunner"`.

### [Low] Challenge 3: Clean Architecture Boundary Violation
- **Assumption challenged**: The architecture maintains strict separation of concerns and package dependency rules.
- **Attack scenario**: `HushNavigation` in the `ui` package directly imports and manipulates `OnboardingPrefs` from `com.hush.app.data.pref`.
- **Root Cause**: Coupling between UI and Data layers. Direct reference to concrete data implementations from navigation shell.
- **Blast radius**: Changes to preference keys or data storage engines require changing the UI navigation package, violating data independence.
- **Mitigation**: Abstract onboarding status checking into a `domain` usecase/repository interface (e.g. `UserRepository` or `GetOnboardingStatusUseCase`), and have the `ui` layer query the interface.

---

## Stress Test Results

### 1. Build and Compile times (Clean & Incremental)
- **Scenario**: Perform clean build and run unit tests.
- **Expected Behavior**: Successful build and unit test execution under 10 seconds.
- **Actual Behavior**: 
  - `clean test` executes successfully.
  - Production compile is stable and fast: **5 seconds** for clean build, **<1 second** for incremental.
  - **No unit tests exist** (`:app:testDebugUnitTest NO-SOURCE` and `:app:testReleaseUnitTest NO-SOURCE`).
- **Result**: **PASS** (for compilation/build performance of production code, though unit tests are empty).

### 2. Dependency Graph and Circular Dependency Check
- **Scenario**: Verify absence of circular dependencies at module and package levels.
- **Expected Behavior**: Strict directional graph: UI -> Domain, Data -> Domain, DI -> *.
- **Actual Behavior**: 
  - Single-module project structure makes Gradle module-level circular dependencies impossible.
  - Package dependencies are strictly directional except for the `ui` -> `data.pref.OnboardingPrefs` coupling noted above. No circular references exist between `domain`, `data`, and `ui` packages.
- **Result**: **PASS** (no circular dependencies).

---

## Unchallenged Areas
- **On-device E2E Verification**: Unable to run or verify instrumented tests on an emulator/device due to the compilation failure.

---

## Additional Warnings & Recommendations
1. **AGP version compatibility**: AGP version `8.5.0` generates a compile warning when building with `compileSdk = 35`. Consider updating AGP or adding `android.suppressUnsupportedCompileSdk=35` to `gradle.properties` to suppress.
2. **Icon Deprecations**: The following warnings are emitted during compilation:
   - `ScreenRoute.kt:20:64`: `@property:Deprecated(...) val Icons.Filled.Send`
   - `ScreenRoute.kt:21:67`: `@property:Deprecated(...) val Icons.Filled.List`
   - `ChatScreen.kt:107:53`: `@property:Deprecated(...) val Icons.Filled.Send`
   - *Recommendation*: Migrate to `Icons.AutoMirrored.Filled.Send` and `Icons.AutoMirrored.Filled.List`.
