# Handoff Report - Phase 2 (Adversarial Coverage Hardening)

## 1. Observation
- **File**: `app/src/main/java/com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt`
  - Lines 57-65:
    ```kotlin
    val pattern = rule.matchPattern
    if (pattern != null && textToEvaluate != null) {
        fieldMatches = when (rule.matchType) {
            MatchType.CONTAINS -> textToEvaluate.contains(pattern, ignoreCase = true)
            MatchType.EXACT -> textToEvaluate.equals(pattern, ignoreCase = true)
            MatchType.REGEX -> runCatching { Regex(pattern).containsMatchIn(textToEvaluate) }.getOrDefault(false)
        }
    } else if (pattern == null) {
        fieldMatches = true
    }
    ```
  - Lines 67-69:
    ```kotlin
    if (rule.isInverted) {
        fieldMatches = !fieldMatches
    }
    ```
- **File**: `app/src/main/java/com/hush/app/data/db/entity/RuleEntity.kt`
  - Line 45:
    ```kotlin
    timeStart = timeStart?.let { LocalTime.parse(it) },
    ```
- **File**: `app/src/main/java/com/hush/app/data/db/dao/RuleDao.kt`
  - Line 17-18:
    ```kotlin
    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun getActiveRules(): List<RuleEntity>
    ```
- **Command Output**:
  - Running `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hush.app.e2e.AdversarialTest` yields:
    ```
    > Task :app:connectedDebugAndroidTest
    Starting 5 tests on test_device(AVD) - 15
    Finished 5 tests on test_device(AVD) - 15
    BUILD SUCCESSFUL in 4s
    ```
- **Generated Test Suite**: Saved at `app/src/androidTest/java/com/hush/app/e2e/AdversarialTest.kt` containing 5 Tier 5 test cases.

## 2. Logic Chain
1. **Inverted Sender Rule Vulnerability**:
   - *Observation*: If `textToEvaluate` is `null` (e.g. sender is null in system alerts/notifications) and `pattern` is not null (e.g. "Manager"), the condition `pattern != null && textToEvaluate != null` is false.
   - *Observation*: The `else if (pattern == null)` block is skipped.
   - *Inference*: `fieldMatches` remains `false`.
   - *Observation*: If `rule.isInverted` is `true`, `fieldMatches` becomes `!fieldMatches` which is `true`.
   - *Conclusion*: Inverted block rules matching on sender (like "Block all unless sender is Manager") will matching-and-block notifications with `null` senders (like system alerts), leading to unwanted blocks of critical system UI elements.
2. **Invalid Regex under Inversion**:
   - *Observation*: In `EvaluateNotificationUseCase.kt`, `MatchType.REGEX` constructs `Regex(pattern)`. If it is malformed, `runCatching` catches the parsing exception and returns `false`.
   - *Inference*: When `isInverted` is true, the `false` match is negated to `true`.
   - *Conclusion*: A malformed regex inverted rule matches everything, causing unintended blocking or muting actions.
3. **Database DateTimeParseException Crash**:
   - *Observation*: In `RuleEntity.toDomain()`, `LocalTime.parse(it)` parses SQLite string times.
   - *Inference*: If a rule is saved with an invalid time format (e.g., `"12:00 PM"`), `LocalTime.parse` throws `DateTimeParseException`.
   - *Conclusion*: Because `toDomain()` maps rules during retrieval in `getActiveRules()`, an invalid string in the database crashes the notification listener evaluation flow on every incoming notification.
4. **Non-Deterministic Priority Ties**:
   - *Observation*: `RuleDao.getActiveRules()` orders by `priority ASC`.
   - *Inference*: When two rules have equal priority values, SQLite ordering is non-deterministic (dependent on internal SQLite page allocation/rowid).
   - *Conclusion*: Rule evaluation for priority ties is unstable and depends on the order of insertion.

## 3. Caveats
- No direct testing of the Gemini Nano model outputs; mocked domain rules were inserted directly to evaluate the database and parsing robustness.
- Background process binding conflicts with instrumentation runner were bypassed by executing tests at the Use Case level, avoiding persistent listener UI interactions.

## 4. Conclusion
- Added a new, comprehensive adversarial test suite (`AdversarialTest.kt`) under `app/src/androidTest/java/com/hush/app/e2e/` with 5 Tier 5 test cases addressing the identified gaps. All tests pass successfully under local execution.

## 5. Verification Method
- Execute the following command from the project root:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.hush.app.e2e.AdversarialTest
  ```
- Verify that 5/5 tests execute and succeed.
