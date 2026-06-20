# Verification and Challenge Report (Milestone 1)

## Challenge Summary

**Overall risk assessment**: **MEDIUM**

While the project skeleton compiles and runs successfully under JDK 17, two critical build-time and compatibility risks have been identified:
1. **JDK 26 Build Failure**: Compiling the project using the latest default Homebrew Java (OpenJDK 26) fails during the Java compilation step (`compileDebugJavaWithJavac` / JdkImageTransform).
2. **AGP Compatibility Warnings**: The Android Gradle Plugin version in use (8.5.0) is not officially tested or supported for `compileSdk = 35` (which was introduced after AGP 8.5.0), leading to compile-time warnings.
3. **No JVM Unit Tests**: There are no local unit tests configured under `app/src/test`. Running `./gradlew test` executes successfully but does not run any test code (`NO-SOURCE`).

---

## Performance & Metrics

- **Host Environment**: macOS (Mac), OpenJDK 17.0.19, Android SDK platform-35.
- **Clean Build (Debug)**: ~5.0 seconds (`./gradlew clean assembleDebug`).
- **Cached Build (Debug)**: ~0.4 seconds (`./gradlew assembleDebug` after clean build).
- **Test Compilation & Execution Time**: ~0.6 seconds (`./gradlew test` runs 0 tests due to `NO-SOURCE`).

---

## Dependency & Cycle Analysis

We performed package-level grep scans and dependency tracing to verify the absence of circular dependencies:
- **Module-Level**: The project has only one Gradle module (`:app`), so there are no inter-module dependency cycles.
- **Package-Level**: Follows Clean Architecture separation:
  - `com.hush.app.domain` has no imports from `data`, `di`, `service`, or `ui`.
  - `com.hush.app.data` has no imports from `ui` or `service`.
  - `com.hush.app.ui` has no imports from `data` or `service`.
  - `com.hush.app.service` has no imports from `data` or `ui`.
  - No package outside of `di` (and mocks in test code) imports from `di`.
- **Navigation Hierarchy**: Strict top-down hierarchy: `MainActivity` -> `HushNavigation` -> `MainScreen` -> individual Screen Composables (`ChatScreen`, `RulesScreen`, `HistoryScreen`, `SettingsScreen`), with zero upward or sibling import cycles.

---

## Challenges

### [High] Challenge 1: Default Brew Java (JDK 26) Incompatibility

- **Assumption challenged**: The build process works seamlessly with any Java 17+ runtime, including the default runtime installed on a developer's machine.
- **Attack scenario**: A developer uses the default system OpenJDK installed via Homebrew (currently JDK 26.0.1) and runs `./gradlew assembleDebug`. The build fails with:
  ```
  Execution failed for task ':app:compileDebugJavaWithJavac'.
  > Could not resolve all files for configuration ':app:androidJdkImage'.
     > Failed to transform core-for-system-modules.jar to match attributes ...
        > Execution failed for JdkImageTransform: .../core-for-system-modules.jar.
           > Error while executing process .../bin/jlink ...
  ```
- **Blast radius**: Developers cannot compile or build the app on modern macOS configurations without explicitly setting `JAVA_HOME` to JDK 17.
- **Mitigation**: 
  1. Specify the Java Toolchain version in `app/build.gradle.kts`:
     ```kotlin
     kotlin {
         jvmToolchain(17)
     }
     ```
  2. Document the JDK 17 requirement in `README.md` or add a `.sdkmanrc` file.

### [Medium] Challenge 2: Untested compileSdk Version on AGP 8.5.0

- **Assumption challenged**: AGP 8.5.0 is fully compatible with Android 15 / SDK 35.
- **Attack scenario**: Compiling or packaging the project outputs:
  ```
  WARNING: We recommend using a newer Android Gradle plugin to use compileSdk = 35
  This Android Gradle plugin (8.5.0) was tested up to compileSdk = 34.
  ```
  Using untested AGP configurations can cause unexpected compiler errors or dexing failures.
- **Blast radius**: Medium risk of build failures or runtime instability when utilizing SDK 35 platform components.
- **Mitigation**: Update Android Gradle Plugin (`com.android.application`) to `8.6.0` or higher in `gradle/libs.versions.toml`, or add `android.suppressUnsupportedCompileSdk=35` to `gradle.properties` to suppress the warning if verified stable.

### [Medium] Challenge 3: Lack of JVM Local Unit Tests

- **Assumption challenged**: The local test suite (`./gradlew test`) is configured and ready to validate business logic during development.
- **Attack scenario**: A developer introduces a regression in the rule evaluation or metadata parsing logic (e.g. `EvaluateNotificationUseCase`). Running `./gradlew test` passes in 0.6 seconds but runs 0 tests (`NO-SOURCE`), completely missing the bug.
- **Blast radius**: High risk of shipping functional regressions. Developers must run slow instrumented/E2E tests (`connectedAndroidTest` on emulator/device) to catch logic bugs.
- **Mitigation**: Co-locate unit tests for pure logic classes (such as `EvaluateNotificationUseCase`) under `app/src/test/java/com/hush/app/domain/usecase/` to enable fast, host-side unit testing.

---

## Stress Test Results

| Scenario | Expected Behavior | Actual Behavior | Pass/Fail |
|---|---|---|---|
| Run `./gradlew clean assembleDebug` with OpenJDK 17 | Compile and package successfully in < 10 seconds | Successful compile and package in ~5.0 seconds | **PASS** |
| Run `./gradlew clean assembleDebug` with OpenJDK 26 | Compile and package successfully | Fails during `:app:compileDebugJavaWithJavac` due to `jlink` error | **FAIL** |
| Run `./gradlew test` on host system | Run all JVM unit tests | Runs task but reports `NO-SOURCE` (0 tests run) | **FAIL (Empty Suite)** |
| Trace package imports | No circular package dependencies | Verified zero package-level or file-level cycles | **PASS** |

---

## Unchallenged Areas

- **Instrumented/E2E Tests Execution**: The actual execution of `connectedAndroidTest` was not stress-tested because running instrumented tests requires a connected Android Emulator/device with Google APIs (specifically for Gemini Nano/AICore mock verification).
