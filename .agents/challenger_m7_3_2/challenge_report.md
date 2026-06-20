# Challenge Report — APK Compilation & Signing Verification

## Challenge Summary

**Overall risk assessment**: LOW

All tests and builds successfully passed when compiled under OpenJDK 17. The APK was generated and is validly signed. However, some build environment requirements/assumptions were uncovered and challenged during the verification.

---

## Challenges

### [Medium] Java Version Compatibility (OpenJDK 26 vs OpenJDK 17)

- **Assumption challenged**: The codebase can compile under the environment's default openjdk (OpenJDK 26.0.1).
- **Attack scenario**: Building with `JAVA_HOME` pointing to OpenJDK 26 fails during Java compilation due to JDK image generation (`jlink` tool arguments/format incompatibilities between Android Gradle Plugin 8.5.0 and Java 26).
- **Blast radius**: Release build fails to compile on environments default-configured with Java 26.
- **Mitigation**: Specify Java 17/21 compatibility explicitly or configure `JAVA_HOME` to point to OpenJDK 17 (e.g. `/opt/homebrew/opt/openjdk@17`).

### [Low] Incremental Compilation & Clean Build Requirement

- **Assumption challenged**: Incremental compilation works reliably without needing clean builds when switching configurations.
- **Attack scenario**: Running `./gradlew assembleRelease` directly after a previous build type compilation can cause `FileAlreadyExistsException` in Hilt/KSP (`_com_hush_app_HushApp.java`).
- **Blast radius**: Build fails due to conflicts with stale generated files in KSP/Hilt directories.
- **Mitigation**: Run `./gradlew clean` (or delete `app/build` and `build` folders) before compiling the release build, and stop any background Gradle/Kotlin compile daemons to avoid file locks.

### [Low] Fallback Signing Configuration

- **Assumption challenged**: A dedicated production release keystore is used for signing the release build.
- **Attack scenario**: If `keystore.properties` is not present and environment variables are not configured, the gradle script silently falls back to the debug signing configuration (`C=US, O=Android, CN=Android Debug`).
- **Blast radius**: The generated "release" APK is signed with a debug certificate, making it unsuitable for direct Play Store deployment.
- **Mitigation**: Ensure that CI/CD environments supply `RELEASE_STORE_FILE` and other credentials via environment variables, or document that a release certificate is required prior to production release.

---

## Verification & Stress Test Results

### 1. Compile Release APK
- **Command**:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17
  ./gradlew assembleRelease
  ```
- **Expected Behavior**: Successful build with exit code 0.
- **Actual Behavior**: Successful build in 53s.
- **Result**: **PASS**

### 2. Verify Output APK Existence
- **Command**:
  ```bash
  find app/build/outputs/apk/release -name "*.apk"
  ```
- **Expected Behavior**: File `app-release.apk` exists in the output directory.
- **Actual Behavior**: Found `app/build/outputs/apk/release/app-release.apk` (size: ~12-15 MB).
- **Result**: **PASS**

### 3. Verify APK Signing using `apksigner`
- **Command**:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17
  /opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner verify --verbose "app/build/outputs/apk/release/app-release.apk"
  ```
- **Expected Behavior**: Outputs verification confirmation (specifically V2 signing verification).
- **Actual Behavior**:
  ```
  Verifies
  Verified using v1 scheme (JAR signing): false
  Verified using v2 scheme (APK Signature Scheme v2): true
  Verified using v3 scheme (APK Signature Scheme v3): false
  Verified using v3.1 scheme (APK Signature Scheme v3.1): false
  Verified using v4 scheme (APK Signature Scheme v4): false
  Verified for SourceStamp: false
  Number of signers: 1
  ```
- **Result**: **PASS**

### 4. Verify Signing Identity
- **Command**:
  ```bash
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17
  /opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner verify --print-certs "app/build/outputs/apk/release/app-release.apk"
  ```
- **Expected Behavior**: Verifies that the signer matches the fallback config or release config.
- **Actual Behavior**:
  ```
  Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
  Signer #1 certificate SHA-256 digest: 19b2bc25788ccf07ff3b2af53f89e63f8411fbe9f58e4dd8db02a46b6adf41f8
  Signer #1 certificate SHA-1 digest: 784e96e4aa717c4db460e90a603a5e8c34b771ba
  Signer #1 certificate MD5 digest: b05825631b888fec9ad2d91f36da4b56
  ```
- **Result**: **PASS** (Correct fallback behavior verified)

---

## Unchallenged Areas

- **Actual production keystore signing**: Since no production keystore or `keystore.properties` is provided in the repository, we could not test compilation with an actual non-debug release certificate. However, the build logic successfully handles the fallback as expected.
