# Forensic Audit & Fix Strategy Analysis — Milestone 1 (Project Skeleton)

## Executive Summary
This document analyzes three critical issues in the Hush Android app: the manual rule-matching engine mock in `RealWorldScenarioE2ETest.kt`, the missing time-window logic in `EvaluateNotificationUseCase.kt`, and the logging discrepancy regarding non-matching notifications. It proposes a unified, clean fix strategy aligning with Clean Architecture, Hilt dependency injection, and best practices for performance/privacy.

---

## Issue 1: Hardcoded Mock Shortcut in `RealWorldScenarioE2ETest.kt`
- **Location**: `app/src/androidTest/java/com/hush/app/e2e/RealWorldScenarioE2ETest.kt` (lines 84–159, inside `simulateNotificationPost`).
- **Problem**: The end-to-end (E2E) test contains an inline, duplicated implementation of the rule matching and logging logic, bypassing the production code path (`EvaluateNotificationUseCase.kt`). This creates high maintenance overhead, leaves code behavior unverified, and risks testing diverging from actual app execution.

### Proposed Fix Strategy
Replace the mock shortcut with a direct call to the production `EvaluateNotificationUseCase.execute()`.

#### Step 1: Add Inject Declaration to `RealWorldScenarioE2ETest`
First, inject the production use case using Hilt. Add the following import and property definition:
```kotlin
// Import to add:
import com.hush.app.domain.usecase.EvaluateNotificationUseCase

// Property injection inside RealWorldScenarioE2ETest class:
@Inject
lateinit var evaluateNotificationUseCase: EvaluateNotificationUseCase
```

Since the class is annotated with `@HiltAndroidTest`, the `EvaluateNotificationUseCase` dependency will be automatically resolved and injected during the `@Before setup()` phase when `hiltRule.inject()` is called. Under the hood, Hilt provides the `RuleRepositoryImpl` and `HistoryRepositoryImpl` dependencies to the use case constructor.

#### Step 2: Refactor `simulateNotificationPost`
Replace the local matching/logging logic inside `simulateNotificationPost` with direct delegation:
```kotlin
private fun simulateNotificationPost(
    packageName: String,
    appName: String,
    title: String?,
    text: String?,
    sender: String?,
    currentTime: LocalTime = LocalTime.now()
): Boolean = runBlocking {
    evaluateNotificationUseCase.execute(
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        sender = sender,
        currentTime = currentTime
    ) == RuleAction.BLOCK
}
```

#### Step 3: Handle Parameters
All properties are forwarded to `execute()` cleanly. The `currentTime` parameter (used in time-window tests) is passed down so that the use case can evaluate rule suitability using the mocked test time.

---

## Issue 2: Missing Time Window Logic in Production Use Case
- **Location**: `app/src/main/java/com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt`
- **Problem**: The production use case `EvaluateNotificationUseCase.kt` completely ignores `timeStart` and `timeEnd` attributes of rules, evaluating all notifications as matching the time constraint.

### Proposed Fix Strategy
Design and implement a robust time-window comparison inside the rule evaluation loop.

#### 1. Time Representations in the System
- **Database Entity (`RuleEntity.kt`)**: Stores time windows as ISO-8601 strings, e.g. `"22:00"` and `"07:00"`.
- **Domain Model (`Rule.kt`)**: Stores them parsed as `java.time.LocalTime?` (via `RuleEntity.toDomain()`).
- **Use Case (`EvaluateNotificationUseCase.kt`)**: Works directly with domain `Rule` objects, so `rule.timeStart` and `rule.timeEnd` are already of type `LocalTime?`.

#### 2. Robust Time Range Matching Logic
To correctly evaluate if a notification is received within a rule's active window:
- **Normal Range (Simple)**: `timeStart` <= `timeEnd` (e.g. 09:00 to 17:00). A time matches if `currentTime` is in `[timeStart, timeEnd]`.
- **Overnight Range (Crosses Midnight)**: `timeStart` > `timeEnd` (e.g. 22:00 to 07:00). A time matches if `currentTime` is `>= timeStart` **OR** `<= timeEnd`.

#### 3. Exact Production Kotlin Implementation
Modify `EvaluateNotificationUseCase.execute(...)` as follows:

```kotlin
// In app/src/main/java/com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt

// 1. Add java.time.LocalTime import
import java.time.LocalTime

// 2. Update execute signature
suspend fun execute(
    packageName: String,
    appName: String,
    title: String?,
    text: String?,
    sender: String?,
    currentTime: LocalTime = LocalTime.now() // Enables time mocking in tests
): RuleAction {
    val rules = ruleRepository.getActiveRules()
    var matchedRuleId: Long? = null
    var matchedRuleName: String? = null
    var action = RuleAction.ALLOW

    for (rule in rules) {
        val appMatches = rule.appPackage == null || rule.appPackage == packageName
        if (!appMatches) continue

        // Evaluate time window
        if (rule.timeStart != null && rule.timeEnd != null) {
            val inWindow = if (rule.timeStart.isAfter(rule.timeEnd)) {
                // Overnight window (e.g. 22:00 to 07:00)
                !currentTime.isBefore(rule.timeStart) || !currentTime.isAfter(rule.timeEnd)
            } else {
                // Standard daytime window (e.g. 09:00 to 17:00)
                !currentTime.isBefore(rule.timeStart) && !currentTime.isAfter(rule.timeEnd)
            }
            if (!inWindow) continue
        }

        var fieldMatches = false
        val textToEvaluate = when (rule.matchField) {
            MatchField.TITLE -> title
            MatchField.TEXT -> text
            MatchField.SENDER -> sender
            MatchField.ANY -> "${title ?: ""} ${text ?: ""} ${sender ?: ""}"
        }

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

        if (rule.isInverted) {
            fieldMatches = !fieldMatches
        }

        if (fieldMatches) {
            matchedRuleId = rule.id
            matchedRuleName = rule.name
            action = rule.action
            break
        }
    }

    // Log logic based on selected strategy (see Issue 3)
```

---

## Issue 3: Test Assertion and Name Discrepancy
- **Location**: `NotificationInterceptionE2ETest.kt` (`testInterception_NoMatchingRules_AllowsNotificationWithoutLogs`) and `EvaluateNotificationUseCase.kt`.
- **Problem**: The test name and documentation state that notifications that do not match any rules should be allowed *without creating logs*. However, the test asserts that exactly 1 log of type `ALLOW` is written, and `EvaluateNotificationUseCase` logs everything unconditionally.

### Two Alignment Strategies

#### Approach A: Only Log When a Rule Matches (`matchedRuleId != null`) — RECOMMENDED
In this approach, the use case writes history records only when the notification matches an explicit rule configured by the user.

- **Why it is cleaner (Recommended)**:
  1. **Storage & Performance**: Android devices receive hundreds of notifications daily. Storing an entry for every single non-matched notification will rapidly bloat the SQLite database, causing performance degradation and storage pressure over time.
  2. **Privacy**: Recording details (titles, senders, text content) of every notification—even those irrelevant to "Hush" rules—introduces unnecessary data collection on the device.
  3. **User Value**: A history log cluttered with hundreds of default "ALLOW" entries makes it harder for the user to review what rules actually fired and when.

- **Proposed Code Changes in `EvaluateNotificationUseCase.kt`**:
```kotlin
    // Log history only when a rule matched
    if (matchedRuleId != null) {
        val event = NotificationEvent(
            appName = appName,
            packageName = packageName,
            title = title ?: "No Title",
            text = text ?: "No Content",
            sender = sender,
            timestamp = Instant.now(),
            actionTaken = action,
            matchedRuleId = matchedRuleId,
            matchedRuleName = matchedRuleName
        )
        historyRepository.insertLog(event)
    }

    return action
```

- **Required Test Assertion Changes**:
  1. In `NotificationInterceptionE2ETest.kt`:
     - Update `testInterception_NoMatchingRules_AllowsNotificationWithoutLogs` to assert `0` logs:
       ```kotlin
       val logs = logDao.getAllLogsFlow().first()
       assertEquals(0, logs.size)
       ```
     - Update `testInterception_RuleDisabled_BypassesInterception` to assert `0` logs (since a disabled rule does not match):
       ```kotlin
       val logs = logDao.getAllLogsFlow().first()
       assertEquals(0, logs.size)
       ```
  2. In `RealWorldScenarioE2ETest.kt`:
     - Update `testScenario_FreshInstallOnboardingAndVoiceRuleCreation()` (since it now delegates to `EvaluateNotificationUseCase`, it will execute the new logging policy):
       ```kotlin
       // Verify history contains only matching logs (Mom log was not matched, so it is not logged)
       runBlocking {
           val logs = logDao.getAllLogsFlow().first()
           val amazonLog = logs.firstOrNull { it.sender == "Amazon" }
           val momLog = logs.firstOrNull { it.sender == "Mom" }
           assertNotNull(amazonLog)
           assertEquals("MUTE", amazonLog!!.actionTaken)
           assertNull(momLog) // Changed from assertNotNull to assertNull
       }
       ```

#### Approach B: Unconditionally Log Everything
If the product requirement mandates that all intercepted notifications must be logged:
- **Action**: Keep the unconditional `historyRepository.insertLog(event)` call in the use case.
- **Rename Test**: Rename `testInterception_NoMatchingRules_AllowsNotificationWithoutLogs` to `testInterception_NoMatchingRules_AllowsNotificationAndLogsDefaultAllow`.
- **Modify Documentation**: Update docstrings in test methods to clarify that implicit allows are stored.

This approach is discouraged due to the storage, performance, and privacy drawbacks highlighted above.

---

## Conclusion
The recommended strategy is:
1. Inject and delegate to `EvaluateNotificationUseCase` in `RealWorldScenarioE2ETest.kt`.
2. Add boundary-inclusive, cross-midnight time window comparison inside `EvaluateNotificationUseCase.kt`.
3. Adopt Approach A for logging, only storing history records for rule-matching events. Update test assertions in `NotificationInterceptionE2ETest` and `RealWorldScenarioE2ETest` to reflect 0 logs for implicit allows.
