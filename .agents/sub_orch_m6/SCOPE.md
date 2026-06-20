# Scope: Milestone 6 (Onboarding & Polish)

## Architecture
Milestone 6 refines the application onboarding flow, permission integration, Material You theming persistence, and general interface animations/polish.
The components include:
- **UI (Onboarding)**: `OnboardingScreen` steps, state persistence in SharedPreferences (`onboarding_completed`), permission requests.
- **UI (Settings)**: `SettingsScreen` reflecting permissions, theme choice persistence, database retention pruning configurations.
- **UI (Theme)**: Material 3 / Material You theme observation in `MainActivity` matching user preferences (Light, Dark, System).

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | Onboarding & Permission Check | Verify the onboarding step transitions, rationales for denied permissions, and SharedPreferences storage | none | PLANNED |
| 2 | Theme Customization & Persistence | Ensure the dynamic colors and Light/Dark/System theme options persist across recreation and update instantly | M1 | PLANNED |
| 3 | Database Retention Pruning | Ensure background database pruning functions trigger correctly based on settings options (7, 30, 90 days) | M2 | PLANNED |
| 4 | UI Polish & Animations | Integrate polished navigation screen transitions, button ripples, and warning banner fade-ins | M3 | PLANNED |
| 5 | Verification & Audit | Verify compiling, pass all E2E tests (including `AppFoundationE2ETest.kt`), and secure a CLEAN Forensic Auditor verdict | M4 | PLANNED |

## Interface Contracts

### Onboarding Flow States
- Step 0: Welcome Screen
- Step 1: Permission requests (Notification interception [mandatory], Microphone [optional], Battery exemption [optional]).
- Step 2: AICore system capability verification.
- If notification permission is denied, show rational banner `"onboarding_deny_rationale"` and disable the next step.

### Settings UI Configs
- Status badges: `"settings_notification_status"`, `"settings_voice_status"` displaying "Active" / "Inactive".
- Theme option click: `"settings_theme_pref"` -> option dropdown/dialog options `"settings_theme_dark_option"`, `"settings_theme_system_option"`.
- Retention click: `"settings_retention_pref"` -> options `"settings_retention_7_days"`, `"settings_retention_30_days"`, `"settings_retention_90_days"`.
