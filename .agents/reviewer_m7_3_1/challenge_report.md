# Challenge Report

## Challenge Summary

**Overall risk assessment**: MEDIUM

While the release configuration compiles and runs successfully under standard conditions, there are two notable risk vectors associated with the signing fallback logic and configuration resolution.

---

## Challenges

### [High] Challenge 1: Silent Fallback to Debug Signatures in Production/CI
- **Assumption challenged**: The build system assumes that falling back to a debug signature is always safe if the release keystore properties/environment variables are not found.
- **Attack scenario**: If a CI pipeline is misconfigured (e.g., environment variables are missing, or a credential rotation fails), the release build will silently fall back to using the local debug signature and finish successfully. The CI system then publishes or archives a debug-signed build.
- **Blast radius**: A debug-signed build is uploaded to production distribution channels (e.g., Google Play Store or internal testing), allowing anyone with access to local debug keys to intercept or replicate the build certificate, or preventing users from upgrading due to signature mismatches.
- **Mitigation**: Disallow debug signature fallback when building on CI systems. This can be done by checking an environment variable (like `System.getenv("CI") != null`) and throwing a Gradle configuration exception if the release keys are missing on CI.

### [Medium] Challenge 2: Cryptic Failures on Partially Configured Release Signing
- **Assumption challenged**: The build system assumes that if `RELEASE_STORE_FILE` is defined, the release credentials are fully configured.
- **Attack scenario**: If a developer sets `RELEASE_STORE_FILE` but forgets to set `RELEASE_STORE_PASSWORD` or `RELEASE_KEY_ALIAS` (e.g., due to env typos or local keychain omissions), the fallback logic will evaluate `storeFile?.exists() == true` as true, and thus select the `release` signing config. The build will then crash late in the packaging phase with a cryptic signing error (e.g. `Password verification failed` or null pointer exception).
- **Blast radius**: Increased developer frustration and delayed build cycles due to late-stage build failures with non-descriptive error messages.
- **Mitigation**: Add validation within the signing configuration block. If `storeFilePath` is present, verify that the store password, key alias, and key password are also non-empty. If any are missing, either fall back to debug or fail early in the configuration phase with a clear error message.

---

## Stress Test Results

- **Scenario: Clean build with daemon locking**  
  → Expected: Successful clean and build  
  → Actual: Fails with Kotlin daemon compile issues (`lateinit property cleanFilenames has not been initialized`) and directory resource locking  
  → **FAIL** (Mitigated by running `./gradlew --stop` to kill active daemons)

- **Scenario: Missing release properties**  
  → Expected: Build falls back to debug signing configuration  
  → Actual: Correctly falls back to debug signing configuration and compiles  
  → **PASS**

- **Scenario: Relative keystore path in keystore.properties**  
  → Expected: File resolved relative to root directory  
  → Actual: File resolved relative to `app/` subproject directory, failing `exists()` check and falling back to debug  
  → **FAIL** (Path resolution scope mismatch)

---

## Unchallenged Areas

- **R8/Proguard rules optimization** — Reason not challenged: Beyond the scope of release signing configuration. However, since `isMinifyEnabled = false` is set for release builds, the actual Proguard optimization and obfuscation rules are not active, which leaves the app size and code security unchallenged.
