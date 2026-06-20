# Adversarial Challenge Report: Build Configuration & Speeds (Milestone 1)

## Challenge Summary

**Overall risk assessment**: MEDIUM

While the project skeleton compiles successfully and has extremely fast compilation speeds (13s clean build, 355ms incremental up-to-date), the build toolchain has several incompatibilities, warnings, and architectural contradictions that present medium-term risks for stability, CI/CD execution, and database migrations.

---

## Challenges

### [Medium] Challenge 1: Version Mismatch between AGP 8.5.0 and compileSdk 35
- **Assumption challenged**: Compiling against SDK 35 (Android 15) using Android Gradle Plugin 8.5.0 is safe and production-ready.
- **Attack scenario**: As the codebase introduces Android 15 specific APIs or resources, AGP 8.5.0 (tested only up to SDK 34) may fail to compile or package them correctly, resulting in build failures or undefined runtime behavior.
- **Blast radius**: Compilation of UI/Resource files, packaging of resources, or crash-inducing bytecode generation for SDK 35 APIs.
- **Mitigation**: Upgrade AGP version to `8.6.0` or higher to natively support SDK 35, or suppress the warning by adding `android.suppressUnsupportedCompileSdk=35` to `gradle.properties` (only as a temporary workaround).

### [Medium] Challenge 2: Daemon Directory Locking during Clean Tasks
- **Assumption challenged**: Running `./gradlew clean` will always succeed on local and CI developer setups.
- **Attack scenario**: When the build folder is cleared or modified, active Gradle file watchers and the daemon process lock directories inside `app/build/`, causing the clean task to crash with:
  `java.io.IOException: Unable to delete directory '/Users/vipinsingh/Documents/Antigravity/open source/hush/app/build'`
- **Blast radius**: Local developers and CI runner builds failing intermittently when they execute clean tasks or switch branches.
- **Mitigation**: Ensure Gradle daemon is stopped (`./gradlew --stop`) before performing manual filesystem cleanups, and ensure JVM memory properties and daemon configs are stabilized.

### [Low] Challenge 3: Room Schema Configuration Contradiction
- **Assumption challenged**: Setting `room.schemaLocation` is consistent with database schema management.
- **Attack scenario**: In `app/build.gradle.kts`, the Room schema location is configured (`room.schemaLocation`), but in `HushDatabase.kt`, schema exporting is disabled (`exportSchema = false`). This creates a contradiction: Room ignores the build configuration, and no schemas are exported, preventing database migration history tracking.
- **Blast radius**: Blocks the verification of database schemas during migrations.
- **Mitigation**: Update `HushDatabase.kt` to `@Database(..., exportSchema = true)` or remove the redundant `room.schemaLocation` from `app/build.gradle.kts`.

### [Low] Challenge 4: Deprecated Material Icons Compilation Warnings
- **Assumption challenged**: The compiler output is warning-free.
- **Attack scenario**: Compiling Kotlin sources produces deprecation warnings for `Icons.Filled.Send` and `Icons.Filled.List`.
- **Blast radius**: Warning pollution makes it harder to identify critical compile-time warning signals.
- **Mitigation**: Replace the deprecated icons with `Icons.AutoMirrored.Filled.Send` and `Icons.AutoMirrored.Filled.List`.

---

## Stress Test Results

| Scenario / Hypothesis | Expected Behavior | Actual Behavior | Pass/Fail |
|---|---|---|---|
| **Clean Build Speed** (`./gradlew clean assembleDebug`) | Complete compilation from scratch in under 30s | Succeeded in **13s** | **PASS** |
| **Incremental Build Speed** (no change) | Check all 40 tasks and finish in under 2s | Succeeded in **355ms** | **PASS** |
| **Incremental Build Speed** (with change) | Recompile changed targets in under 10s | Succeeded in **5s** | **PASS** |
| **Room KSP Generation** | Compile DAO and entities without errors/warnings | Successfully generated `HushDatabase_Impl.java`, `RuleDao_Impl.java`, and `NotificationLogDao_Impl.java` with 0 warnings. | **PASS** |
| **Clean Build after Manual Delete** | Build successfully with active daemon | Failed with `java.io.IOException: Unable to delete directory` until daemon stopped. | **FAIL** |

---

## Unchallenged Areas

- **Instrumented Test Execution** — Could not run instrumented tests (`connectedAndroidTest`) because no Android Emulator or physical device was attached.
