# Handoff Report: Milestone 3 (Rule Engine) Investigation

This report summarizes the read-only static analysis of the Milestone 3 Rule Engine implementation in the Hush app. 

---

## 1. Observation

Direct observations made in the codebase:

### A. Priority Ordering Logic
*   **Rule DAO Query:** In `com/hush/app/data/db/dao/RuleDao.kt` line 17:
    ```kotlin
    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun getActiveRules(): List<RuleEntity>
    ```
*   **Evaluation Iterator:** In `com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt` lines 32-77:
    ```kotlin
    for (rule in rules) {
        // First match triggers action and breaks the loop
        if (fieldMatches) {
            matchedRuleId = rule.id
            matchedRuleName = rule.name
            action = rule.action
            break
        }
    }
    ```
*   **Incrementer:** In `com/hush/app/data/repository/RuleRepositoryImpl.kt` lines 48-51:
    ```kotlin
    override suspend fun getNextPriority(): Int {
        val maxPriority = ruleDao.getMaxPriority()
        return (maxPriority ?: 0) + 1
    }
    ```
*   **E2E Test Precedence Expectation:** In `app/src/androidTest/java/com/hush/app/e2e/RealWorldScenarioE2ETest.kt` line 183:
    ```kotlin
    // T4_RW_03: Verify priority order (lower priority value = higher precedence)
    ```
*   **Hardcoded priority in AI Chat:** In `com/hush/app/ui/screens/chat/ChatScreen.kt` line 294:
    ```kotlin
    priority = 0,
    ```

### B. Time Window Evaluation
*   **Check Condition:** In `com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt` lines 37-46:
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

### C. SwipeToDismissBox Layout & Styling
*   **Rules Screen Layout:** In `com/hush/app/ui/screens/rules/RulesScreen.kt` lines 78-116:
    ```kotlin
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Red.copy(alpha = 0.8f))
            )
        },
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedRule = rule }
                    .testTag("rule_card_${rule.id}")
            ) { ... }
        }
    )
    ```
*   **Dismiss Confirmation Filter:** In `com/hush/app/ui/screens/rules/RulesScreen.kt` lines 67-76:
    ```kotlin
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                viewModel.deleteRule(rule)
                true
            } else {
                false
            }
        }
    )
    ```

### D. SQLite Foreign Key Enforcement
*   **Database Builder:** In `com/hush/app/di/DatabaseModule.kt` lines 24-30:
    ```kotlin
    return Room.databaseBuilder(
        context,
        HushDatabase::class.java,
        HushDatabase.DATABASE_NAME
    )
    .fallbackToDestructiveMigration()
    .build()
    ```
*   **Foreign Key Annotation:** In `com/hush/app/data/db/entity/NotificationLogEntity.kt` lines 11-20:
    ```kotlin
    @Entity(
        tableName = "notification_logs",
        foreignKeys = [
            ForeignKey(
                entity = RuleEntity::class,
                parentColumns = ["id"],
                childColumns = ["matchedRuleId"],
                onDelete = ForeignKey.SET_NULL
            )
        ],
        ...
    )
    ```

### E. Redundancy in RoomConverters
*   **RoomConverters:** In `com/hush/app/data/db/RoomConverters.kt` lines 18-26:
    ```kotlin
    @TypeConverter
    fun fromTimeString(value: String?): LocalTime? {
        return value?.let { LocalTime.parse(it) }
    }
    ```
*   **Manual Entity Mapping:** In `com/hush/app/data/db/entity/RuleEntity.kt` lines 45-46:
    ```kotlin
    timeStart = timeStart?.let { LocalTime.parse(it) },
    timeEnd = timeEnd?.let { LocalTime.parse(it) },
    ```

### F. UI App Identifier
*   **Rules Screen Display:** In `com/hush/app/ui/screens/rules/RulesScreen.kt` line 104:
    ```kotlin
    Text(rule.appPackage ?: "All Apps", style = MaterialTheme.typography.bodySmall)
    ```

---

## 2. Logic Chain

1.  **Priority Evaluation Logic Mismatch:**
    *   `RuleDao` sorts by `priority ASC`, which delivers lower priority values first.
    *   `EvaluateNotificationUseCase` returns early on the first match.
    *   This means lower priority values have *higher* precedence (0 executes before 1).
    *   However, `RuleRepositoryImpl.getNextPriority()` yields `maxPriority + 1` (a larger number).
    *   Consequently, newly created rules get larger priority values, sorting them *last* and giving them *lower* precedence than older rules.
    *   Furthermore, `ChatScreen` bypasses this entirely by hardcoding `priority = 0`. As a result, all rules created via chat end up with priority 0, neutralizing the priority ordering.

2.  **Bypassing Partial Time Windows:**
    *   The conditional block `if (rule.timeStart != null && rule.timeEnd != null)` is only entered if *both* values are present.
    *   If a rule has only a start time or only an end time (e.g., set to block after 10 PM, without setting a mornings limit), the block is skipped.
    *   Therefore, the rule behaves as active 24/7, failing to respect the configured single boundary.

3.  **SwipeToDismissBox visual/UX flaws:**
    *   The `Card` has rounded corners by default.
    *   The red background `Box` has no shape specified (defaults to rectangle) and no padding. During a swipe, the sharp rectangular red block will leak outside the card's rounded corners.
    *   The background lacks any text or icon to inform the user that it triggers deletion.
    *   `SwipeToDismissBox` allows both left-to-right (StartToEnd) and right-to-left (EndToStart) swipes. Since `confirmValueChange` only handles `EndToStart` for deletion and rejects `StartToEnd` (returns false), swiping left-to-right is uselessly allowed, revealing the red background but not deleting on release.

4.  **Database Foreign Key Integrity Gap:**
    *   SQLite disables foreign keys by default on Android.
    *   The database builder in `DatabaseModule.kt` does not run `PRAGMA foreign_keys = ON;` in `onOpen()`.
    *   Thus, deleting a rule from the `rules` table will NOT automatically set `matchedRuleId = NULL` in corresponding `notification_logs` at runtime, leaving dangling/orphaned references in history logs.

5.  **Unused TypeConverters Redundancy:**
    *   `RoomConverters` declares converters for `LocalTime` and `Instant`.
    *   `RuleEntity` declares its database properties as primitive `String` and `Long` columns, mapping manually.
    *   This renders the declared type converters redundant, adding dead code to the database layer.

---

## 3. Caveats

*   **Runtime Verification:** Due to the absence of a configured Java Runtime on the explorer environment path, instrumented tests and unit tests could not be run locally. The analysis is strictly static.
*   **Priorities Design Intent:** We assume standard priority systems expect higher priority values (e.g., 10) to execute before lower ones (e.g., 0). If the project explicitly desires "0 is highest precedence and new rules should be evaluated last," the ASC sorting and `getNextPriority` incrementing is theoretically consistent with that, but the hardcoded `priority = 0` in `ChatScreen` still breaks it.

---

## 4. Conclusion

The Milestone 3 Rule Engine implementation contains several gaps and inconsistencies:
1.  **Priority conflict:** Ascending DAO sort order + incrementing `getNextPriority()` gives newer rules lower precedence. Hardcoded priority `0` in `ChatScreen` renders priority ordering moot.
2.  **Time logic flaw:** Single-bound time ranges are bypassed entirely, resulting in 24/7 active rules.
3.  **UI/UX gaps:** Sharp rectangular red background leaks past rounded card corners on swipe; no delete icon; left-to-right swipes are allowed but do nothing. Raw package name (`appPackage`) is shown on cards instead of `appDisplayName`.
4.  **Database integrity gap:** Foreign keys are not enabled via `PRAGMA foreign_keys = ON;` callback, resulting in orphaned logs when rules are deleted.
5.  **Dead code:** Redundant `LocalTime` and `Instant` TypeConverters are registered in `RoomConverters` but bypassed in `RuleEntity`.

---

## 5. Verification Method

### A. Priority Ordering Logic
1.  Inspect `app/src/main/java/com/hush/app/data/db/dao/RuleDao.kt` (lines 14 & 17) to verify sorting is `priority ASC`.
2.  Inspect `app/src/main/java/com/hush/app/data/repository/RuleRepositoryImpl.kt` (lines 48-51) to verify it increments priority via `(maxPriority ?: 0) + 1`.
3.  Inspect `app/src/main/java/com/hush/app/ui/screens/chat/ChatScreen.kt` (line 294) to confirm new rules are saved with hardcoded `priority = 0`.

### B. Time Range Bypassing
1.  Inspect `app/src/main/java/com/hush/app/domain/usecase/EvaluateNotificationUseCase.kt` (lines 37-46) to verify that only rules with both `timeStart != null` and `timeEnd != null` evaluate time window constraints.

### C. SwipeToDismissBox
1.  Inspect `app/src/main/java/com/hush/app/ui/screens/rules/RulesScreen.kt` (lines 78-87) to verify `backgroundContent` is a simple rectangle `Box` with no shape or icon.
2.  Observe that `SwipeToDismissBox` has no `directions` parameter, permitting both swipe directions.

### D. Foreign Key Constraints
1.  Inspect `app/src/main/java/com/hush/app/di/DatabaseModule.kt` (lines 19-31) and confirm there is no `.addCallback` implementation enabling SQLite foreign key enforcement at runtime.
