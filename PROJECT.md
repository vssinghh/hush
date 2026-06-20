# Project: Hush

## Architecture
Hush is a Kotlin + Jetpack Compose Android app targeting SDK 35 (min SDK 33).
The package structure follows Clean Architecture:
- `com.hush.app.ui`: Compose UI (Screens, ViewModels, Theme, Navigation)
- `com.hush.app.domain`: Business logic, domain models (Rule, NotificationEvent, RuleAction, ParsedCommand), repositories interfaces, and use cases (ParseCommandUseCase, EvaluateNotificationUseCase, ManageRulesUseCase, QueryHistoryUseCase).
- `com.hush.app.data`: Data access implementations, Room DB entities, DAOs, repositories, Gemini Nano engine (AICore integration), and SpeechRecognizer wrapper.
- `com.hush.app.service`: HushNotificationListener service implementing NotificationListenerService.
- `com.hush.app.di`: Dagger Hilt modules.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | M1: Project Skeleton | Gradle setup, Hilt, Room DB, Material You theme, bottom nav, navigation shell | none | DONE |
| 2 | M2: Notification Listener | HushNotificationListener service, permission flow, notification metadata extraction, history logging | M1 | DONE |
| 3 | M3: Rule Engine | Rule model, Room CRUD, rule evaluation logic, rules management UI | M2 | DONE |
| 4 | M4: AI Integration | Gemini Nano via AICore, prompt design, ParseCommandUseCase, command -> rule pipeline | M3 | DONE |
| 5 | M5: Chat UI + Voice | Chat screen with conversation history, SpeechRecognizer integration, voice input button with waveform | M4 | DONE |
| 6 | M6: Onboarding + Polish | First-launch flow, permission explanations, Material You theming, animations, error handling, edge cases | M5 | DONE |
| 7 | M7: Release Prep | README with screenshots, LICENSE, GitHub Actions CI, APK signing, release build | M6 | DONE |

## Interface Contracts
### AI Parse Output format
The Gemini Nano model must output a JSON structure:
```json
{
  "action": "block" | "allow" | "mute",
  "app": "package.name" | null,
  "matchField": "title" | "text" | "sender" | "any",
  "matchType": "contains" | "regex" | "exact",
  "matchPattern": "string" | null,
  "isInverted": boolean,
  "timeStart": "HH:mm" | null,
  "timeEnd": "HH:mm" | null,
  "summary": "human-readable description"
}
```

### Rule Evaluation Logic Contract
- Match app package if app is specified.
- Evaluate matchField (title, text, sender, any) with matchType (contains, regex, exact) using matchPattern.
- If isInverted is true, the match is negated (e.g. ALLOW if it does NOT match pattern).
- Evaluate if current time falls within timeStart to timeEnd window.
- Apply rule action (ALLOW, BLOCK, MUTE).

## Code Layout
- `app/src/main/java/com/hush/app/`
