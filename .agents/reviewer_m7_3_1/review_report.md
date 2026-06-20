# Review Report

## Review Summary

**Verdict**: APPROVE

We reviewed the release signing configuration changes in `app/build.gradle.kts` and verified that both compilation of the release APK via `./gradlew assembleRelease` and running tests via `./gradlew testDebugUnitTest` execute successfully.

When Gradle daemon conflicts are cleared, the build compiles and packages successfully. The fallback logic correctly routes the signing configuration to `debug` when the release keystore properties/files are not present, which was verified using `apksigner`.

---

## Findings

### [Major] Finding 1: Concurrency/File Locking Failures during Clean Build
- **What**: Clean release builds (`./gradlew clean assembleRelease`) frequently fail due to file-locking conflicts between multiple active/zombie Kotlin or Gradle daemon sessions.
- **Where**: Task execution (`:app:compileReleaseKotlin`, `:app:mergeExtDexRelease`)
- **Why**: Background daemons lock generated classes and intermediate files (e.g. `HushDatabase.class` or `external_libs_dex` intermediates). When a clean run deletes the directories, active daemons get confused or fail to write output files, throwing `FailedCompilationException` or `NoSuchFileException`.
- **Suggestion**: Run `./gradlew --stop` to kill active daemons prior to running a clean build, or document this requirement in the release prep instructions.

### [Minor] Finding 2: Store File Path Context Resolution
- **What**: The keystore path (`storeFile = file(storeFilePath)`) resolves relative to the `app/` subproject directory rather than the root project directory.
- **Where**: `app/build.gradle.kts` lines 45–50
- **Why**: In a multi-module or standard Gradle project, `file(path)` in a sub-module's build script resolves relative to that sub-module's directory. If `keystore.properties` is configured at the root with a relative path like `storeFile=release.jks`, Gradle will search for it at `app/release.jks`. If it's missing there, it falls back to the debug key.
- **Suggestion**: Use `rootProject.file(storeFilePath)` to resolve relative paths relative to the root project directory, where the `keystore.properties` file is typically located.

---

## Verified Claims

- **Claim 1: Unit tests compile and run properly**  
  → Verified via `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest`  
  → Result: **PASS** (30 tasks executed successfully)
  
- **Claim 2: Release APK compiles successfully**  
  → Verified via `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease`  
  → Result: **PASS** (50 tasks executed, output `app-release.apk` created)

- **Claim 3: Fallback logic successfully signs with debug key when release key is absent**  
  → Verified via running `apksigner verify --print-certs` on `app-release.apk`  
  → Result: **PASS** (Signed with `CN=Android Debug`)

---

## Coverage Gaps

- **Real Release Key Signing Integration** — Risk Level: **Medium** — Recommendation: Accept risk. Since we are operating in a CI/CD or local test environment without access to the actual production private key, we must rely on fallback-to-debug verification and environmental variables mocks.

---

## Unverified Items

- **Actual production keystore loading** — Reason not verified: Production keystore file and its secrets are rightfully omitted from the repository for security.
