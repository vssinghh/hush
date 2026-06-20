# Original User Request

## 2026-06-20T04:15:53Z

You are the Project Orchestrator. Execute the Hush Android app project as described in /Users/vipinsingh/Documents/Antigravity/open source/hush/ORIGINAL_REQUEST.md and the implementation plan at /Users/vipinsingh/.gemini/antigravity/brain/254de90a-80da-4745-a4fc-ba492deac66b/implementation_plan.md. Create your workspace directory at /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/orchestrator/ and maintain a progress.md file in that directory. Keep me updated on your progress, and notify me once all milestones are complete.

## 2026-06-20T04:17:41Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m1/SCOPE.md.
[diff_block_start]
@@ -1,12 +1,13 @@
-# Scope: Milestone 1 — Project Skeleton
+# Scope: Milestone 1 (Project Skeleton)
 
 ## Architecture
-- Setup Gradle build files (Kotlin DSL) targeting SDK 35 (min SDK 33).
-- Configure Room Database with entities RuleEntity and NotificationLogEntity.
-- Configure Hilt dependency injection.
-- Create Material You / Material 3 theme.
-- Create Single Activity (MainActivity.kt) and a navigation graph (HushNavGraph.kt) with four bottom navigation tabs: Chat, Rules, History, Settings.
-- Implement Onboarding Screen placeholder.
+- Root & app-level Kotlin DSL Gradle build script configuration targeting SDK 35 and min SDK 33.
+- Dagger Hilt DI initialization (Hilt application, Hilt gradle plugins, and DI injection points).
+- Room DB configuration (Database, entities, and DAOs skeletons, module provider).
+- Material 3 / Material You theme configuration (Color.kt, Theme.kt, Type.kt).
+- Bottom Navigation bar with four tabs: Chat, Rules, History, Settings.
+- Jetpack Compose Navigation Graph routing between onboarding and main dashboard, handling onboarding completion state in SharedPreferences.
+- Screens skeletons for Onboarding, Chat, Rules, History, Settings.
 
 ## Milestones
 | # | Name | Scope | Dependencies | Status |
@@ -13,4 +13,12 @@
-| 1 | Gradle & Architecture Setup | Setup project files, gradle configurations, Hilt and Room DB configurations | none | PLANNED |
-| 2 | Navigation & Theme Setup | Setup Material 3/You theme, navigation graph, bottom navigation with 4 tabs | M1 | PLANNED |
-| 3 | Onboarding Skeleton | Setup onboarding view state and permissions check logic placeholder | M2 | PLANNED |
+| 1 | M1.1: Gradle & Hilt setup | Create project structure, configuration files, and Dagger Hilt application class | none | PLANNED |
+| 2 | M1.2: Room DB Skeleton | Setup DB entities, DAO interface, AppDatabase, and Hilt modules | M1.1 | PLANNED |
+| 3 | M1.3: Navigation & UI Theme | Establish navigation graph, Material 3 dynamic theme, bottom navigation tabs | M1.1 | PLANNED |
+| 4 | M1.4: Screen Skeletons | Skeleton compose views for Chat, Rules, History, Settings, and Onboarding screen | M1.3 | PLANNED |
+| 5 | M1.5: Verification | Run gradle build to ensure compilation is 100% clean and correct | M1.1-M1.4 | PLANNED |
+
+## Interface Contracts
+### Main Navigation Route Contract
+- Onboarding Screen -> Main Screen (containing Bottom Navigation)
+- Bottom Navigation -> [Chat, Rules, History, Settings]
+- SharedPreferences key `onboarding_completed` (boolean) to determine launch screen.
[diff_block_end]

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_e2e/SCOPE.md.
[diff_block_start]
@@ -1,8 +1,10 @@
-# Scope: E2E Testing Track
+# Scope: E2E Test Suite Creation
 
 ## Architecture
-- Designs a comprehensive, opaque-box test suite derived from user requirements.
-- Uses Android instrumented tests (`./gradlew connectedAndroidTest`) and XML journey tests if applicable.
+Opaque-box E2E testing framework for the Hush Android app.
+- Testing technology: Jetpack Compose UI Testing (`ComposeTestRule`), Android Instrumented Tests (`androidTest`), and Hilt test injection where necessary (using `HiltAndroidRule`).
+- Test location: `app/src/androidTest/java/com/hush/app/`
+- Custom test runner: `com.hush.app.HushTestRunner` (extends `AndroidJUnitRunner` to handle Hilt dependency setup).
 
 ## Milestones
 | # | Name | Scope | Dependencies | Status |
@@ -9,9 +9,18 @@
-| 1 | Test Infra Setup | Design and configure test runner, instrumentation helpers | none | PLANNED |
-| 2 | Tier 1 (Feature Coverage) | Implement >=20 Tier 1 tests (5 per feature: App Foundation, Interception, Conversational AI, Rules Manager) | M1 | PLANNED |
-| 3 | Tier 2 (Boundary & Corner) | Implement >=20 Tier 2 tests (boundaries, edge cases, error-prone inputs) | M2 | PLANNED |
-| 4 | Tier 3 (Cross-Feature) | Implement >=4 Tier 3 tests (pairwise feature combinations) | M3 | PLANNED |
-| 5 | Tier 4 (Real-World Application) | Implement >=5 Tier 4 tests (application-level scenarios) | M4 | PLANNED |
+| 1 | M1: Test Infrastructure Setup | Custom test runner, build.gradle instrumentation configs, Hilt test environment, base test classes | none | PLANNED |
+| 2 | M2: Tier 1 Feature Coverage | Happy-path tests (>=5 tests per feature, total 20+ cases) covering onboarding, notification listener, AI parsing rules, rules/history screens | M1 | PLANNED |
+| 3 | M3: Tier 2 Boundary & Edge Cases | Boundary/error tests (>=5 per feature, total 20+ cases) covering denied permissions, empty lists, long commands, invalid AI formats, notification database limits | M2 | PLANNED |
+| 4 | M4: Tier 3 Cross-Feature Interactions | Pairwise interactions of features (>=4 tests) covering workflow transitions like onboarding -> AI rule creation, AI rule creation -> Notification interception | M3 | PLANNED |
+| 5 | M5: Tier 4 Real-World Workloads | Real-world journey tests (5 scenarios: onboarding+mute, active time window, rule priority, inverted rules, settings retention) | M4 | PLANNED |
+| 6 | M6: Publish TEST_READY.md | Verify all tests build and run, compile test suite summary, publish TEST_READY.md | M5 | PLANNED |
 
 ## Interface Contracts
-- Must publish `TEST_READY.md` at project root once all tests (Tiers 1-4) are successfully written and ready to run.
+### Test Runner Config
+- Test runner: `com.hush.app.HushTestRunner`
+- Instrumentation arguments: default
+
+### Feature Coverage Checklist
+- Feature 1: App Foundation (Onboarding, MVVM layers, bottom nav tabs)
+- Feature 2: Notification Interception (metadata extraction, Room DB logs, rule matching)
+- Feature 3: Conversational AI (Gemini Nano output parser, voice integration stub/recording, package resolver)
+- Feature 4: Rule Management & History (Rules screen toggle/delete, History filter/search, Settings retention)
[diff_block_end]

## 2026-06-20T04:21:35Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_e2e/SCOPE.md.
[diff_block_start]
@@ -9,7 +9,7 @@
 ## Milestones
 | # | Name | Scope | Dependencies | Status |
 |---|---|---|---|---|
-| 1 | M1: Test Infrastructure Setup | Custom test runner, build.gradle instrumentation configs, Hilt test environment, base test classes | none | PLANNED |
+| 1 | M1: Test Infrastructure Setup | Custom test runner, build.gradle instrumentation configs, Hilt test environment, base test classes | none | DONE |
 | 2 | M2: Tier 1 Feature Coverage | Happy-path tests (>=5 tests per feature, total 20+ cases) covering onboarding, notification listener, AI parsing rules, rules/history screens | M1 | PLANNED |
 | 3 | M3: Tier 2 Boundary & Edge Cases | Boundary/error tests (>=5 per feature, total 20+ cases) covering denied permissions, empty lists, long commands, invalid AI formats, notification database limits | M2 | PLANNED |
 | 4 | M4: Tier 3 Cross-Feature Interactions | Pairwise interactions of features (>=4 tests) covering workflow transitions like onboarding -> AI rule creation, AI rule creation -> Notification interception | M3 | PLANNED |
[diff_block_end]

## 2026-06-20T04:24:52Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_e2e/SCOPE.md.
[diff_block_start]
@@ -10,11 +10,11 @@
 | # | Name | Scope | Dependencies | Status |
 |---|---|---|---|---|
 | 1 | M1: Test Infrastructure Setup | Custom test runner, build.gradle instrumentation configs, Hilt test environment, base test classes | none | DONE |
-| 2 | M2: Tier 1 Feature Coverage | Happy-path tests (>=5 tests per feature, total 20+ cases) covering onboarding, notification listener, AI parsing rules, rules/history screens | M1 | PLANNED |
-| 3 | M3: Tier 2 Boundary & Edge Cases | Boundary/error tests (>=5 per feature, total 20+ cases) covering denied permissions, empty lists, long commands, invalid AI formats, notification database limits | M2 | PLANNED |
-| 4 | M4: Tier 3 Cross-Feature Interactions | Pairwise interactions of features (>=4 tests) covering workflow transitions like onboarding -> AI rule creation, AI rule creation -> Notification interception | M3 | PLANNED |
-| 5 | M5: Tier 4 Real-World Workloads | Real-world journey tests (5 scenarios: onboarding+mute, active time window, rule priority, inverted rules, settings retention) | M4 | PLANNED |
-| 6 | M6: Publish TEST_READY.md | Verify all tests build and run, compile test suite summary, publish TEST_READY.md | M5 | PLANNED |
+| 2 | M2: Tier 1 Feature Coverage | Happy-path tests (>=5 tests per feature, total 20+ cases) covering onboarding, notification listener, AI parsing rules, rules/history screens | M1 | DONE |
+| 3 | M3: Tier 2 Boundary & Edge Cases | Boundary/error tests (>=5 per feature, total 20+ cases) covering denied permissions, empty lists, long commands, invalid AI formats, notification database limits | M2 | DONE |
+| 4 | M4: Tier 3 Cross-Feature Interactions | Pairwise interactions of features (>=4 tests) covering workflow transitions like onboarding -> AI rule creation, AI rule creation -> Notification interception | M3 | DONE |
+| 5 | M5: Tier 4 Real-World Workloads | Real-world journey tests (5 scenarios: onboarding+mute, active time window, rule priority, inverted rules, settings retention) | M4 | DONE |
+| 6 | M6: Publish TEST_READY.md | Verify all tests build and run, compile test suite summary, publish TEST_READY.md | M5 | DONE |
 
 ## Interface Contracts
 ### Test Runner Config

## 2026-06-20T05:42:11Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m1/SCOPE.md.
[diff_block_start]
@@ -12,11 +12,11 @@
 ## Milestones
 | # | Name | Scope | Dependencies | Status |
 |---|---|---|---|---|
-| 1 | M1.1: Gradle & Hilt setup | Create project structure, configuration files, and Dagger Hilt application class | none | PLANNED |
-| 2 | M1.2: Room DB Skeleton | Setup DB entities, DAO interface, AppDatabase, and Hilt modules | M1.1 | PLANNED |
-| 3 | M1.3: Navigation & UI Theme | Establish navigation graph, Material 3 dynamic theme, bottom navigation tabs | M1.1 | PLANNED |
-| 4 | M1.4: Screen Skeletons | Skeleton compose views for Chat, Rules, History, Settings, and Onboarding screen | M1.3 | PLANNED |
-| 5 | M1.5: Verification | Run gradle build to ensure compilation is 100% clean and correct | M1.1-M1.4 | PLANNED |
+| 1 | M1.1: Gradle & Hilt setup | Create project structure, configuration files, and Dagger Hilt application class | none | DONE |
+| 2 | M1.2: Room DB Skeleton | Setup DB entities, DAO interface, AppDatabase, and Hilt modules | M1.1 | DONE |
+| 3 | M1.3: Navigation & UI Theme | Establish navigation graph, Material 3 dynamic theme, bottom navigation tabs | M1.1 | DONE |
+| 4 | M1.4: Screen Skeletons | Skeleton compose views for Chat, Rules, History, Settings, and Onboarding screen | M1.3 | DONE |
+| 5 | M1.5: Verification | Run gradle build to ensure compilation is 100% clean and correct | M1.1-M1.4 | BLOCKED: Gen 2 Forensic Audit Failure / Reviewer requested changes |
 
 ## Interface Contracts
 ### Main Navigation Route Contract
[diff_block_end]

## 2026-06-20T06:13:29Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m1/SCOPE.md.
[diff_block_start]
@@ -16,7 +16,7 @@
 | 2 | M1.2: Room DB Skeleton | Setup DB entities, DAO interface, AppDatabase, and Hilt modules | M1.1 | DONE |
 | 3 | M1.3: Navigation & UI Theme | Establish navigation graph, Material 3 dynamic theme, bottom navigation tabs | M1.1 | DONE |
 | 4 | M1.4: Screen Skeletons | Skeleton compose views for Chat, Rules, History, Settings, and Onboarding screen | M1.3 | DONE |
-| 5 | M1.5: Verification | Run gradle build to ensure compilation is 100% clean and correct | M1.1-M1.4 | BLOCKED: Gen 2 Forensic Audit Failure / Reviewer requested changes |
+| 5 | M1.5: Verification | Run gradle build to ensure compilation is 100% clean and correct | M1.1-M1.4 | DONE |
 
 ## Interface Contracts
 ### Main Navigation Route Contract
[diff_block_end]

## 2026-06-20T06:14:08Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m2/SCOPE.md.
[diff_block_start]
@@ -1,18 +1,30 @@
 # Scope: Milestone 2 — Notification Listener & History
 
 ## Architecture
-- Build `HushNotificationListener` service inheriting from `NotificationListenerService`.
-- Intercept notifications, extract metadata: app name, package, title, text, sender (if available), timestamp.
-- Integrate with Room database (`NotificationLogEntity` and `NotificationLogDao`) to persist the history logs.
-- Perform temporary skeleton rule matching evaluation to either allow, block (dismiss), or mute incoming notifications (before rules engine is fully complete in M3).
-- Implement history retention clean up logic in repository (retention configurability).
-
-## Milestones
+Hush intercepts incoming system notifications using `HushNotificationListener` (a `NotificationListenerService`).
+It extracts notification metadata (package, title, text, sender, timestamp) and evaluates them using `EvaluateNotificationUseCase`.
+If a rule match triggers `RuleAction.BLOCK`, the notification is dismissed (`cancelNotification`).
+Events are saved into the Room DB logs via `HistoryRepository`.
+The retention settings in `SettingsScreen` control the pruning of the history logs in Room DB.
+
+## Sub-Milestones
 | # | Name | Scope | Dependencies | Status |
 |---|---|---|---|---|
-| 1 | M2.1: Notification Interception | Create HushNotificationListener service and manifest declarations | none | PLANNED |
-| 2 | M2.2: Metadata Extraction | Code metadata extraction logic (title, text, package, sender) | M2.1 | PLANNED |
-| 3 | M2.3: History Room DB Persistence | Implement history table entity, DAO, repository queries, and Hilt modules injection | M2.2 | PLANNED |
-| 4 | M2.4: Evaluation Service Action | Code temporary skeleton rule matching evaluation for allow, block, mute actions | M2.3 | PLANNED |
-| 5 | M2.5: Verification | Run builds and E2E tests for notification interception and logging | M2.1-M2.4 | PLANNED |
+| 1 | Notification Interception Service | Implement `HushNotificationListener.kt` metadata extraction, usecase evaluation, and dismissal. Verify Manifest declaration. | None | PLANNED |
+| 2 | History Log Retention UI & Pruning | Update `SettingsScreen.kt` with "7 Days", "30 Days", "90 Days" retention options. Wire up DB pruning on selection. | 1 | PLANNED |
+| 3 | Dynamic History List UI | Implement `HistoryScreen.kt` and a supporting `HistoryViewModel` (or EntryPoint accessor) to display log list, filter tabs, and search. | 2 | PLANNED |
+| 4 | Dynamic Rules UI | Implement `RulesScreen.kt` and toggle logic to fetch, render, and update rule states from the database. | 3 | PLANNED |
+| 5 | Verify Build & E2E Tests | Run Gradle build and execute instrumented E2E tests (`NotificationInterceptionE2ETest`, `RuleManagementHistoryE2ETest`). | 4 | PLANNED |
+
+## Interface Contracts
+### HushNotificationListener ↔ EvaluateNotificationUseCase
+- Extracts `packageName`, `appName`, `title`, `text`, `sender` from `StatusBarNotification`.
+- Invokes `evaluateNotificationUseCase.execute(...)` on `Dispatchers.Default` thread.
+- Acts on the returned `RuleAction`:
+  - `BLOCK` -> Calls `cancelNotification(sbn.key)`
+  - `MUTE` / `ALLOW` -> Allowed to post (no cancellation).
+
+### History log pruning
+- Shared preference `retention_policy` stores selected setting: "7 Days", "30 Days", "90 Days".
+- Selection immediately invokes `historyRepository.deleteLogsOlderThan(threshold)` with threshold = `Instant.now() - N days`.
[diff_block_end]

## 2026-06-20T16:42:00Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/orchestrator/progress.md.
[diff_block_start]
@@ -1,5 +1,5 @@
 ## Current Status
-Last visited: 2026-06-20T16:40:06Z
+Last visited: 2026-06-20T16:42:00Z
 - [x] Initialize PROJECT.md and TEST_INFRA.md
 - [x] Spawn E2E Testing Track Orchestrator (ID: 04a104bb-8e52-4d65-a47f-dbfaae3f6bd0) [DONE]
 - [x] E2E Testing Track publishes TEST_READY.md
[diff_block_end]

## 2026-06-20T16:49:59Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m2/SCOPE.md.
[diff_block_start]
@@ -10,11 +10,11 @@
 ## Sub-Milestones
 | # | Name | Scope | Dependencies | Status |
 |---|---|---|---|---|
-| 1 | Notification Interception Service | Implement `HushNotificationListener.kt` metadata extraction, usecase evaluation, and dismissal. Verify Manifest declaration. | None | PLANNED |
-| 2 | History Log Retention UI & Pruning | Update `SettingsScreen.kt` with "7 Days", "30 Days", "90 Days" retention options. Wire up DB pruning on selection. | 1 | PLANNED |
-| 3 | Dynamic History List UI | Implement `HistoryScreen.kt` and a supporting `HistoryViewModel` (or EntryPoint accessor) to display log list, filter tabs, and search. | 2 | PLANNED |
-| 4 | Dynamic Rules UI | Implement `RulesScreen.kt` and toggle logic to fetch, render, and update rule states from the database. | 3 | PLANNED |
-| 5 | Verify Build & E2E Tests | Run Gradle build and execute instrumented E2E tests (`NotificationInterceptionE2ETest`, `RuleManagementHistoryE2ETest`). | 4 | PLANNED |
+| 1 | Notification Interception Service | Implement `HushNotificationListener.kt` metadata extraction, usecase evaluation, and dismissal. Verify Manifest declaration. | None | DONE |
+| 2 | History Log Retention UI & Pruning | Update `SettingsScreen.kt` with "7 Days", "30 Days", "90 Days" retention options. Wire up DB pruning on selection. | 1 | DONE |
+| 3 | Dynamic History List UI | Implement `HistoryScreen.kt` and a supporting `HistoryViewModel` (or EntryPoint accessor) to display log list, filter tabs, and search. | 2 | DONE |
+| 4 | Dynamic Rules UI | Implement `RulesScreen.kt` and toggle logic to fetch, render, and update rule states from the database. | 3 | DONE |
+| 5 | Verify Build & E2E Tests | Run Gradle build and execute instrumented E2E tests (`NotificationInterceptionE2ETest`, `RuleManagementHistoryE2ETest`). | 4 | DONE |
 
 ## Interface Contracts
 ### HushNotificationListener ↔ EvaluateNotificationUseCase
[diff_block_end]

## 2026-06-20T16:50:25Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/sub_orch_m3/SCOPE.md.
[diff_block_start]
@@ -1,17 +1,26 @@
-# Scope: Milestone 3 — Rule Engine
+# Scope: Milestone 3 (Rule Engine)
 
 ## Architecture
-- Define Rule entity and database table in Room.
-- Implement Room CRUD operations for Rules (`RuleDao`, `RuleRepository`).
-- Implement the exact rule evaluation logic matching rule dimensions: app, sender/contact, keywords (contains/regex), and time window.
-- Act on RuleAction: Allow, Block, Mute.
-- Implement the Rules Screen UI (list card layout, status toggles, swipe-to-delete, tap to edit details).
+Milestone 3 implements the Rule Engine for the Hush Android app. The components span the Clean Architecture layers:
+- **Domain**: `Rule` model, `RuleRepository` interface, and `EvaluateNotificationUseCase`.
+- **Data**: `RuleEntity` Room representation, `RuleDao` database operations, and `RuleRepositoryImpl`.
+- **UI**: `RulesScreen` Compose screen and `RulesViewModel` to manage screen state.
 
 ## Milestones
 | # | Name | Scope | Dependencies | Status |
-|---|---|---|---|---|
-| 1 | Rule Entity & DB Room CRUD | Implement RuleEntity, RuleDao, and RuleRepository Room CRUD operations | none | PLANNED |
-| 2 | Rule Evaluation Logic | Complete the business logic for rule matching (exact, contains, regex, time-window, inverted matching) | 1 | PLANNED |
-| 3 | Rules Management UI | Implement RulesScreen Compose UI, ViewModels, toggle state updates, swipe to delete | 2 | PLANNED |
-| 4 | Verification & Coverage | Verify build, execute unit tests for all rule-evaluation dimensions, and E2E rule management tests | 3 | PLANNED |
+|---|------|-------|-------------|--------|
+| 1 | Room Configuration | RuleEntity, RuleDao, and HushDatabase Room setup | none | IN_PROGRESS |
+| 2 | Rule Repository | RuleRepository and RuleRepositoryImpl CRUD operations | M1 | IN_PROGRESS |
+| 3 | Evaluation Logic | EvaluateNotificationUseCase exact dimensions, time windows, and inverted matching | M2 | IN_PROGRESS |
+| 4 | Rules Screen UI | RulesScreen Compose layout with toggles and swipe-to-delete, RulesViewModel state mapping | M3 | IN_PROGRESS |
+| 5 | Test Verification | Execution and passing of unit tests and instrumented E2E tests | M4 | IN_PROGRESS |
+
+## Interface Contracts
+- **Rule Evaluation Logic Contract**:
+  - Match app package if app is specified.
+  - Evaluate matchField (title, text, sender, any) with matchType (contains, regex, exact) using matchPattern.
+  - If isInverted is true, the match is negated (e.g. ALLOW if it does NOT match pattern).
+  - Evaluate if current time falls within timeStart to timeEnd window (supports overnight cross-midnight windows).
+  - Apply rule action (ALLOW, BLOCK, MUTE).
+  - Only log event history when a rule is matched.
[diff_block_end]

## 2026-06-20T17:01:00Z

The following changes were made by the USER to: /Users/vipinsingh/Documents/Antigravity/open source/hush/.agents/orchestrator/progress.md.
[diff_block_start]
@@ -1,5 +1,5 @@
 ## Current Status
-Last visited: 2026-06-20T17:00:14Z
+Last visited: 2026-06-20T17:01:00Z
 - [x] Initialize PROJECT.md and TEST_INFRA.md
 - [x] Spawn E2E Testing Track Orchestrator (ID: 04a104bb-8e52-4d65-a47f-dbfaae3f6bd0) [DONE]
 - [x] E2E Testing Track publishes TEST_READY.md
[diff_block_end]
