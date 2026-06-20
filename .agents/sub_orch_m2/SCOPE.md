# Scope: Milestone 2 — Notification Listener & History

## Architecture
Hush intercepts incoming system notifications using `HushNotificationListener` (a `NotificationListenerService`).
It extracts notification metadata (package, title, text, sender, timestamp) and evaluates them using `EvaluateNotificationUseCase`.
If a rule match triggers `RuleAction.BLOCK`, the notification is dismissed (`cancelNotification`).
Events are saved into the Room DB logs via `HistoryRepository`.
The retention settings in `SettingsScreen` control the pruning of the history logs in Room DB.

## Sub-Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | Notification Interception Service | Implement `HushNotificationListener.kt` metadata extraction, usecase evaluation, and dismissal. Verify Manifest declaration. | None | DONE |
| 2 | History Log Retention UI & Pruning | Update `SettingsScreen.kt` with "7 Days", "30 Days", "90 Days" retention options. Wire up DB pruning on selection. | 1 | DONE |
| 3 | Dynamic History List UI | Implement `HistoryScreen.kt` and a supporting `HistoryViewModel` (or EntryPoint accessor) to display log list, filter tabs, and search. | 2 | DONE |
| 4 | Dynamic Rules UI | Implement `RulesScreen.kt` and toggle logic to fetch, render, and update rule states from the database. | 3 | DONE |
| 5 | Verify Build & E2E Tests | Run Gradle build and execute instrumented E2E tests (`NotificationInterceptionE2ETest`, `RuleManagementHistoryE2ETest`). | 4 | DONE |

## Interface Contracts
### HushNotificationListener ↔ EvaluateNotificationUseCase
- Extracts `packageName`, `appName`, `title`, `text`, `sender` from `StatusBarNotification`.
- Invokes `evaluateNotificationUseCase.execute(...)` on `Dispatchers.Default` thread.
- Acts on the returned `RuleAction`:
  - `BLOCK` -> Calls `cancelNotification(sbn.key)`
  - `MUTE` / `ALLOW` -> Allowed to post (no cancellation).

### History log pruning
- Shared preference `retention_policy` stores selected setting: "7 Days", "30 Days", "90 Days".
- Selection immediately invokes `historyRepository.deleteLogsOlderThan(threshold)` with threshold = `Instant.now() - N days`.
