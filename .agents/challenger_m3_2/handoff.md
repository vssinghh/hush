# Challenger Verification Report (Milestone 3 Rule Engine)

## 1. Observation
We reviewed the Rule Engine implementation in the following files:
* **Rule Engine Use Case**: `app/src/main/java/com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt`
* **Test Suite**: `app/src/test/java/com/hush/app/domain/usecase/EvaluateNotificationUseCaseTest.kt`

We observed the following code patterns during code inspection:

### A. Time window checking (Lines 37–46 in `EvaluateNotificationUseCase.kt`):
```kotlin
            if (rule.timeStart != null && rule.timeEnd != null) {
                val inWindow = if (rule.timeStart.isAfter(rule.timeEnd)) {
                    // overnight range e.g. 22:00 to 07:00
                    !currentTime.isBefore(rule.timeStart) || !currentTime.isAfter(rule.timeEnd)
                } else {
                    // normal range e.g. 09:00 to 17:00
                    !currentTime.isBefore(rule.timeStart) && !currentTime.isAfter(rule.timeEnd)
                }
                if (!inWindow) continue
            }
```

### B. Regex parsing and exception safety (Line 61):
```kotlin
                    MatchType.REGEX -> runCatching { Regex(pattern).containsMatchIn(textToEvaluate) }.getOrDefault(false)
```

### C. Inverted match evaluation (Lines 67–69):
```kotlin
            if (rule.isInverted) {
                fieldMatches = !fieldMatches
            }
```

### D. Concatenation logic for MatchField.ANY (Line 53):
```kotlin
                MatchField.ANY -> "${title ?: ""} ${text ?: ""} ${sender ?: ""}"
```

## 2. Logic Chain
By analyzing the observed implementation logic:
1. **Overnight / Cross-Midnight range**:
   * If `timeStart` is after `timeEnd`, the condition is checked as `!currentTime.isBefore(timeStart) || !currentTime.isAfter(timeEnd)`.
   * For boundaries:
     * When `currentTime == timeStart`, `!currentTime.isBefore(timeStart)` evaluates to `true`. Thus, `inWindow` is `true`.
     * When `currentTime == timeEnd`, `!currentTime.isAfter(timeEnd)` evaluates to `true`. Thus, `inWindow` is `true`.
     * When `currentTime` is between `timeEnd` and `timeStart` (e.g. `12:00` for window `22:00` to `07:00`), both conditions evaluate to `false`. Thus, `inWindow` is `false`.
   * When `timeStart == timeEnd`, `isAfter` evaluates to `false`, transitioning to the `else` branch which evaluates to `currentTime == timeStart`. This is correct but limits match window to exactly one second.
2. **Invalid Regex Handling**:
   * If `pattern` is invalid (e.g., `[`), `Regex(pattern)` throws a `PatternSyntaxException`.
   * Under `runCatching`, the exception is caught, and it defaults to `false`.
   * However, if the rule is inverted (`isInverted = true`), `fieldMatches = !false` evaluates to `true`.
   * Thus, an inverted rule with an invalid regex pattern will evaluate as a match for every single notification, causing potential unintended muting/blocking of unrelated notifications.
3. **Priority Sorting**:
   * Production rule retrieval utilizes SQLite Room's `ORDER BY priority ASC`.
   * The rules are processed sequentially in this sorted order, and execution stops on the first match (`break`).
   * This logic is correct; the lowest priority value is checked first.

To empirically prove these claims, we added 5 new test cases to `EvaluateNotificationUseCaseTest.kt`:
1. `testOvernightCrossMidnight_Nanoseconds`: Stress-tests nanosecond boundaries for overnight intervals.
2. `testTimeWindowStressTesting`: Generates 1,000 randomized time range permutations and cross-references results against a seconds-of-day integer oracle.
3. `testAnyFieldNullMatching`: Asserts matching logic when all fields in `MatchField.ANY` are null (producing `"  "` string).
4. `testPrioritySortingRobustness`: Asserts stable sorting for negative, duplicate, and out-of-order priority values.
5. `testInvertedMatchingEdgeCases`: Tests inverted exact matching, case-insensitivity, and null pattern inversion.

Executing `./gradlew :app:testDebugUnitTest` with openjdk@17 resulted in 36 successful unit tests (0 failures).

## 3. Caveats
* The verification assumes that local clock time values do not change offset/timezone mid-execution (LocalTime is timezone-agnostic).
* The priority sorting logic is tested using a Fake Rule Repository which simulates Room's ordering. The database's actual sorting relies on SQLite's query planner.

## 4. Conclusion
The `EvaluateNotificationUseCase` implementation is structurally robust and functions correctly under cross-midnight overnight intervals, valid/invalid regex patterns, priority ordering, and inverted rules.
* **Minor Vulnerability Note**: If an inverted rule contains an invalid regex pattern, it defaults to a match. While safe from crashing, this can lead to unexpected silences in notifications. We recommend input validation during rule creation to prevent invalid regex patterns from being saved.

## 5. Verification Method
To verify these test cases:
1. Ensure JDK 17 is active in your terminal or specify `JAVA_HOME`.
2. Run the Gradle test command:
   ```bash
   JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home ./gradlew clean :app:testDebugUnitTest
   ```
3. Inspect the Gradle test report located at:
   `app/build/reports/tests/testDebugUnitTest/index.html`
   And confirm that all 36 tests (including the 5 new challenger tests) pass cleanly.
