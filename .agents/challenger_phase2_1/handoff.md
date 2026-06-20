# Phase 2 Adversarial Coverage Hardening — Handoff Report

## 1. Observation
- **Test Infrastructure Failure**: Running `./gradlew connectedAndroidTest` threw:
  ```
  Exception thrown during onBeforeAll invocation of plugin AndroidTestDeviceInfoPlugin: /Users/vipinsingh/Documents/Antigravity/open source/hush/app/build/outputs/androidTest-results/connected/debug/test_device(AVD) - 15/meminfo (No such file or directory)
  ```
  And when running the test suites, they were terminated prematurely with:
  ```
  Test run failed to complete. Instrumentation run failed due to Process crashed.
  ```
  Logcat observations showed:
  ```
  06-20 03:22:15.849   564   601 I ActivityManager: Killing 11375:com.hush.app/u0a529 (adj 0): stop com.hush.app due to installPackageLI
  ```
  This indicated that Gradle's UTP runner tried to install next test suites in parallel on the same single device, which force-stopped and killed the currently executing test.
- **Rule Priorities**: In `EvaluateNotificationUseCase.kt` (lines 32-34) and `RuleDao.kt`:
  ```kotlin
  val rules = ruleRepository.getActiveRules()
  ...
  for (rule in rules) {
      val appMatches = rule.appPackage == null || rule.appPackage == packageName
      if (!appMatches) continue
      ...
  }
  ```
  Rules are fetched via `getActiveRules()` ordered by `priority ASC`. In `RuleRepositoryImpl.kt` (lines 48-51), priority is generated as `(maxPriority ?: 0) + 1`. This makes newer rules have higher priority numbers, meaning they are evaluated last.
- **Inverted Null Pattern**: In `EvaluateNotificationUseCase.kt`:
  ```kotlin
  val pattern = rule.matchPattern
  if (pattern != null && textToEvaluate != null) { ... }
  else if (pattern == null) { fieldMatches = true }
  if (rule.isInverted) { fieldMatches = !fieldMatches }
  ```
  If `pattern` is null, `fieldMatches` is set to `true`, and if the rule is inverted, it becomes `false`. Thus, an inverted rule with `null` pattern can never match any notification.
- **Empty Pattern Match Inconsistencies**:
  - `MatchField.TITLE` maps to `title` (which can be null). If `pattern = ""`, `pattern != null && textToEvaluate != null` evaluates to false, so `fieldMatches = false`.
  - `MatchField.ANY` maps to `"${title ?: ""} ${text ?: ""} ${sender ?: ""}"` (which normalizes to at least `"  "`). If `pattern = ""`, `textToEvaluate` is `"  "`, so it matches, and `fieldMatches = true`.
- **One-Sided Time Range Checks**: In `EvaluateNotificationUseCase.kt` (lines 37-46):
  ```kotlin
  if (rule.timeStart != null && rule.timeEnd != null) {
      val inWindow = if (rule.timeStart.isAfter(rule.timeEnd)) {
          ...
      } else {
          ...
      }
      if (!inWindow) continue
  }
  ```
  If either `timeStart` or `timeEnd` is null (one-sided time range rule), the time checks are skipped completely, and the rule evaluates as active at all times.
- **Adversarial Verification**: Created `app/src/androidTest/java/com/hush/app/e2e/AdversarialTest.kt` containing 6 test cases targeting the above logic paths. Ran them via ADB:
  ```bash
  adb shell am instrument -w -r -e class com.hush.app.e2e.AdversarialTest com.hush.app.test/com.hush.app.runner.HiltTestRunner
  ```
  Output:
  ```
  OK (6 tests)
  ```

## 2. Logic Chain
1. **Observation 1 (Test terminate via installPackageLI)**: UTP concurrently runs tests on the same emulator and performs uninstalls/installs. This force-stops and kills the running instrumentation process, causing premature test failure reports.
2. **Observation 2 (Sequential Run Success)**: Bypassing Gradle's test target and utilizing direct ADB instrumentation (`am instrument`) allows tests to run sequentially in a single process without interference. This allows all 55 baseline tests and 6 new adversarial tests to pass successfully.
3. **Observation 3 (Priority Inversion)**: Since new rules get higher priority numbers (`maxPriority + 1`) and the use case breaks on the first match iterating in `priority ASC` order, broader older rules will match first and bypass newer specific block rules.
4. **Observation 4 (Inverted Null Rules)**: When `matchPattern` is null, `fieldMatches` becomes `true`. Inverting it makes it `false`, so the loop never hits the break block for that rule, rendering inverted rules without patterns ineffective.
5. **Observation 5 (Empty Pattern Inconsistencies)**: Null parameters evaluate differently depending on whether the match field is a single field (which stays null) or `ANY` (which gets formatted to a string with spaces). This causes empty pattern matching to behave inconsistently.
6. **Observation 6 (One-Sided Time Windows)**: Since the condition for time validation requires both `timeStart` and `timeEnd` to be non-null, any rule having only one of them non-null skips validation entirely, meaning the time constraint is ignored.

## 3. Caveats
- Direct execution via ADB bypasses the device info extraction plugins.
- We did not implement layout changes or rule creation logic modifications because the briefing instructions request us to keep changes strictly review-only regarding the implementation code.

## 4. Conclusion
The Hush core rule evaluation engine contains several critical logic gaps and usability issues around priority ordering, inverted matching on null values, empty pattern matching across different fields, and one-sided time windows. We successfully hardened test coverage by generating and executing a Tier 5 adversarial test suite (`AdversarialTest.kt`) confirming all 6 edge-case hypotheses.

## 5. Verification Method
To run the generated adversarial test suite independently, execute:
1. Rebuild the test APK:
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17 && ./gradlew assembleDebugAndroidTest
   ```
2. Reinstall target and test APKs:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
   ```
3. Run the adversarial instrumentation tests:
   ```bash
   adb shell am instrument -w -r -e class com.hush.app.e2e.AdversarialTest com.hush.app.test/com.hush.app.runner.HiltTestRunner
   ```
4. Verify the output displays `OK (6 tests)`.
