# Handoff Report — Explorer 2 (Gen 3)

## 1. Observation
I directly observed the following files and details in the codebase:

- **Mock Shortcut in `RealWorldScenarioE2ETest.kt`**:
  - File: `app/src/androidTest/java/com/hush/app/e2e/RealWorldScenarioE2ETest.kt`
  - Function: `simulateNotificationPost` (lines 84–159).
  - Code verbatim:
    ```kotlin
    private fun simulateNotificationPost(
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        sender: String?,
        currentTime: LocalTime = LocalTime.now()
    ): Boolean = runBlocking {
        val rules = ruleDao.getActiveRules() // Ordered by priority ASC in DB
        var matchedRule: RuleEntity? = null
        ...
        val logEntity = NotificationLogEntity(
            ...
        )
        logDao.insertLog(logEntity)
        action == RuleAction.BLOCK
    }
    ```

- **Production Use Case missing Time Window Logic**:
  - File: `app/src/main/java/com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt` (lines 18–80).
  - Current implementation has no reference to `rule.timeStart` or `rule.timeEnd` inside the `for (rule in rules)` loop.
  - Verbatim structure:
    ```kotlin
    suspend fun execute(
        packageName: String,
        appName: String,
        title: String?,
        text: String?,
        sender: String?
    ): RuleAction {
        val rules = ruleRepository.getActiveRules()
        ...
        for (rule in rules) {
            val appMatches = rule.appPackage == null || rule.appPackage == packageName
            if (!appMatches) continue

            var fieldMatches = false
            ...
    ```

- **Logging Discrepancy & Test Name/Assertion Conflict**:
  - File: `app/src/androidTest/java/com/hush/app/e2e/NotificationInterceptionE2ETest.kt`
  - Test Case: `testInterception_NoMatchingRules_AllowsNotificationWithoutLogs` (lines 188–199).
  - Verbatim code:
    ```kotlin
    @Test
    fun testInterception_NoMatchingRules_AllowsNotificationWithoutLogs() = runBlocking {
        // T1_F2_04: Verify that notifications are passed through normally if no rules match
        val isCanceled = simulateNotificationPost("com.instagram", "Instagram", "New message", "Hello", null)

        assertFalse(isCanceled)
        // System either logs default ALLOW or nothing. We check it is not blocked.
        val logs = logDao.getAllLogsFlow().first()
        assertEquals(1, logs.size)
        assertEquals("ALLOW", logs[0].actionTaken)
        assertNull(logs[0].matchedRuleId)
    }
    ```
  - Use case file: `EvaluateNotificationUseCase.kt` unconditionally logs history at the end of `execute()` (lines 65–77):
    ```kotlin
    // Log history
    val event = NotificationEvent(
        ...
    )
    historyRepository.insertLog(event)
    ```

---

## 2. Logic Chain
1. **Delegation to Use Case**: By injecting `EvaluateNotificationUseCase` in `RealWorldScenarioE2ETest.kt` and updating `simulateNotificationPost` to call its `execute()` method, we eliminate the duplicated rule-matching code and ensure test results reflect actual production class behavior (from Observation 1).
2. **Time Window Matching**: In `EvaluateNotificationUseCase`, `rule.timeStart` and `rule.timeEnd` are of type `java.time.LocalTime?` (via data mapping from `RuleEntity.toDomain()`). Adding an optional `currentTime: LocalTime = LocalTime.now()` parameter to `execute()` allows tests to pass mocked times. Within the rule iteration loop, we can evaluate overnight ranges (where `start.isAfter(end)`) using `!currentTime.isBefore(start) || !currentTime.isAfter(end)` and standard day ranges using `!currentTime.isBefore(start) && !currentTime.isAfter(end)`. This ensures correctness for all range constraints (from Observation 2).
3. **Aligning Logs and Assertions**:
   - The test name explicitly states: `testInterception_NoMatchingRules_AllowsNotificationWithoutLogs`.
   - Storing a log database row for *every* notification creates severe storage bloat and unnecessary privacy exposure (from Observation 3).
   - Therefore, the cleaner approach is to wrap the logging logic inside `EvaluateNotificationUseCase.kt` with a condition: `if (matchedRuleId != null)`.
   - Consequently, the test assertion must be updated to expect `0` logs for non-matching notifications.
   - Additionally, tests that expect non-matching logs to exist (such as `testInterception_RuleDisabled_BypassesInterception` in `NotificationInterceptionE2ETest.kt` and `testScenario_FreshInstallOnboardingAndVoiceRuleCreation()` in `RealWorldScenarioE2ETest.kt`) must be updated to expect `0` logs or remove non-matching log assertions.

---

## 3. Caveats
- This investigation assumes that the Android environment supports Java 8 time desugaring (`java.time.LocalTime` and `java.time.Instant`), which is standard for Android API 26+ and is already imported and used elsewhere in the codebase.
- No other background workers or features are assumed to depend on `ALLOW` logs with a `null` matched rule ID (we audited `CrossFeatureE2ETest.kt` and `RuleManagementHistoryE2ETest.kt` and confirmed they do not assert log existence for implicit allows).

---

## 4. Conclusion
We propose:
1. Injecting `EvaluateNotificationUseCase` in `RealWorldScenarioE2ETest.kt` and delegating matching logic to `execute()`.
2. Adding boundary-inclusive, cross-midnight time window comparison inside `EvaluateNotificationUseCase.kt` and supporting an optional `currentTime` parameter.
3. Implementing conditional logging (`if (matchedRuleId != null)`) in the use case and updating related test assertions to expect 0 logs for non-matching notifications.

---

## 5. Verification Method
To independently verify the strategy once implemented, run the following Android instrumentation test command:
```bash
./gradlew connectedAndroidTest
```
Specifically, inspect and ensure the following test cases pass:
- `com.hush.app.e2e.RealWorldScenarioE2ETest`
- `com.hush.app.e2e.NotificationInterceptionE2ETest`
- `com.hush.app.e2e.CrossFeatureE2ETest`
- `com.hush.app.e2e.RuleManagementHistoryE2ETest`

Invalidation conditions:
- If a test fails because `momLog` or other default allow logs are missing, check if the assertions in the test files were correctly updated to expect `0` logs/`null` logs for non-matching instances.
