# E2E Test Infra: Hush

## Test Philosophy
- Opaque-box, requirement-driven. No dependency on implementation design.
- Methodology: Category-Partition + BVA + Pairwise + Workload Testing.

## Feature Inventory
| # | Feature | Source (requirement) | Tier 1 | Tier 2 | Tier 3 |
|---|---------|---------------------|:------:|:------:|:------:|
| 1 | App Foundation | ORIGINAL_REQUEST §R1 | 5 | 5 | ✓ |
| 2 | Notification Interception | ORIGINAL_REQUEST §R2 | 5 | 5 | ✓ |
| 3 | Conversational AI | ORIGINAL_REQUEST §R3 | 5 | 5 | ✓ |
| 4 | Rule Management & History | ORIGINAL_REQUEST §R4 | 5 | 5 | ✓ |

## Test Architecture
- Test runner: `./gradlew connectedAndroidTest` or ADB and journey runner.
- Test case format: Android instrumented tests under `app/src/androidTest/` and journey files.
- Directory layout: `app/src/androidTest/`

## Real-World Application Scenarios (Tier 4)
| # | Scenario | Features Exercised | Complexity |
|---|----------|--------------------|------------|
| 1 | Complete First-Launch and Mute Rule | App Foundation, Conversational AI, Notification Interception | Medium |
| 2 | Time-window Rule During Active Hours | Notification Interception, Rule Management | Medium |
| 3 | Multiple Rules Priority Ordering | Notification Interception, Rule Management | High |
| 4 | Inverted Rule Allowed and Blocked | Notification Interception, Rule Management | High |
| 5 | Clear History and Settings Retention | App Foundation, Rule Management | Medium |

## Coverage Thresholds
- Tier 1: ≥5 per feature
- Tier 2: ≥5 per feature (where boundaries exist)
- Tier 3: pairwise coverage of major feature interactions
- Tier 4: ≥5 realistic application scenarios
