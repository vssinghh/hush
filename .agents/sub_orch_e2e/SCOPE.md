# Scope: E2E Test Suite Creation

## Architecture
Opaque-box E2E testing framework for the Hush Android app.
- Testing technology: Jetpack Compose UI Testing (`ComposeTestRule`), Android Instrumented Tests (`androidTest`), and Hilt test injection where necessary (using `HiltAndroidRule`).
- Test location: `app/src/androidTest/java/com/hush/app/`
- Custom test runner: `com.hush.app.HushTestRunner` (extends `AndroidJUnitRunner` to handle Hilt dependency setup).

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | M1: Test Infrastructure Setup | Custom test runner, build.gradle instrumentation configs, Hilt test environment, base test classes | none | DONE |
| 2 | M2: Tier 1 Feature Coverage | Happy-path tests (>=5 tests per feature, total 20+ cases) covering onboarding, notification listener, AI parsing rules, rules/history screens | M1 | DONE |
| 3 | M3: Tier 2 Boundary & Edge Cases | Boundary/error tests (>=5 per feature, total 20+ cases) covering denied permissions, empty lists, long commands, invalid AI formats, notification database limits | M2 | DONE |
| 4 | M4: Tier 3 Cross-Feature Interactions | Pairwise interactions of features (>=4 tests) covering workflow transitions like onboarding -> AI rule creation, AI rule creation -> Notification interception | M3 | DONE |
| 5 | M5: Tier 4 Real-World Workloads | Real-world journey tests (5 scenarios: onboarding+mute, active time window, rule priority, inverted rules, settings retention) | M4 | DONE |
| 6 | M6: Publish TEST_READY.md | Verify all tests build and run, compile test suite summary, publish TEST_READY.md | M5 | DONE |

## Interface Contracts
### Test Runner Config
- Test runner: `com.hush.app.HushTestRunner`
- Instrumentation arguments: default

### Feature Coverage Checklist
- Feature 1: App Foundation (Onboarding, MVVM layers, bottom nav tabs)
- Feature 2: Notification Interception (metadata extraction, Room DB logs, rule matching)
- Feature 3: Conversational AI (Gemini Nano output parser, voice integration stub/recording, package resolver)
- Feature 4: Rule Management & History (Rules screen toggle/delete, History filter/search, Settings retention)
