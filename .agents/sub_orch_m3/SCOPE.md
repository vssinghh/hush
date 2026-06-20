# Scope: Milestone 3 (Rule Engine)

## Architecture
Milestone 3 implements the Rule Engine for the Hush Android app. The components span the Clean Architecture layers:
- **Domain**: `Rule` model, `RuleRepository` interface, and `EvaluateNotificationUseCase`.
- **Data**: `RuleEntity` Room representation, `RuleDao` database operations, and `RuleRepositoryImpl`.
- **UI**: `RulesScreen` Compose screen and `RulesViewModel` to manage screen state.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Rule Entity & DB Room CRUD | Implement RuleEntity, RuleDao, and RuleRepository Room CRUD operations | none | DONE |
| 2 | Rule Evaluation Logic | Complete the business logic for rule matching (exact, contains, regex, time-window, inverted matching) | 1 | DONE |
| 3 | Rules Management UI | Implement RulesScreen Compose UI, ViewModels, toggle state updates, swipe to delete | 2 | DONE |
| 4 | Verification & Coverage | Verify build, execute unit tests for all rule-evaluation dimensions, and E2E rule management tests | 3 | DONE |

## Interface Contracts
- **Rule Evaluation Logic Contract**:
  - Match app package if app is specified.
  - Evaluate matchField (title, text, sender, any) with matchType (contains, regex, exact) using matchPattern.
  - If isInverted is true, the match is negated (e.g. ALLOW if it does NOT match pattern).
  - Evaluate if current time falls within timeStart to timeEnd window (supports overnight cross-midnight windows).
  - Apply rule action (ALLOW, BLOCK, MUTE).
  - Only log event history when a rule is matched.
