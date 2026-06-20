# Hush

Hush is a privacy-first conversational notification interceptor for Android. It runs entirely on-device, leveraging local artificial intelligence (Gemini Nano via Google AICore) and speech recognition to process natural language commands into custom notification filtering rules. All rule storage, notification processing, and execution history log data remain private to your device.

---

## Core Features

- 💬 **Conversational Rule Creation**: Define rules in natural language (e.g., *"Block WhatsApp notifications containing 'spoilers' between 10 PM and 7 AM"*). A pipeline powered by Gemini Nano parses prompts into structured configuration rules.
- 🎧 **Notification Listener Service (`HushNotificationListener`)**: Utilizes Android's `NotificationListenerService` API to intercept, read metadata from, and programmatically dismiss or mute notifications based on evaluated active rules.
- ⚙️ **On-Device Rule Engine & Room Database**: An evaluation engine checks notification metadata (app package, title, text, sender, time windows, and inverted logic exceptions) and executes the corresponding action (`ALLOW`, `BLOCK`, or `MUTE`), storing rules and logs in a secure, local Room SQLite database.
- 🎙️ **SpeechRecognizer Wrapper**: A clean interface wrapping Android's `SpeechRecognizer` API that reports status via a reactive Kotlin Coroutine StateFlow (Idle, Listening, Waveform Updates, Partial/Final Results, Errors), complemented by a voice input sheet featuring a dynamic audio amplitude waveform.
- 🎨 **Material You & Jetpack Compose UI**: Features a modern, unified Compose UI implementing Material You dynamic color styling with five main screens:
  - **Chat screen**: An interactive thread to create rules conversationally with text or voice.
  - **Rules screen**: A repository to view details, toggle active states, or delete rules via swipe.
  - **History screen**: A log viewer showing filtered notifications and applied rule reasons.
  - **Settings screen**: A panel to manage permissions, theme overrides, and database retention pruning.
  - **Onboarding flow**: A guided first-run experience detailing and requesting necessary Android system permissions.

---

## Clean Architecture & Package Structure

The codebase is organized according to Clean Architecture principles to decouple business logic from framework details.

```
com.hush.app/
│
├── di/                     # Dependency Injection modules (Hilt)
│   ├── AIModule.kt         # Binds AIEngine
│   ├── DatabaseModule.kt   # Provides Room Database, RuleDao, and NotificationLogDao instances
│   ├── PermissionModule.kt # Binds PermissionManager
│   ├── PreferencesModule.kt# Provides OnboardingPrefs
│   └── RepositoryModule.kt # Binds Repository implementations
│
├── domain/                 # Business Logic & Core Interfaces (Pure Kotlin)
│   ├── model/              # Domain models and enums
│   │   ├── Rule.kt         # Rule data class, RuleAction, MatchField, MatchType enums
│   │   ├── NotificationEvent.kt # Notification log data class
│   │   └── ParsedCommand.kt # AI-parsed command representation
│   ├── permission/         # Permission-related interfaces
│   │   └── PermissionManager.kt
│   ├── repository/         # Repository interfaces & State definitions
│   │   ├── AIEngine.kt
│   │   ├── HistoryRepository.kt
│   │   ├── PackageResolver.kt
│   │   ├── RuleRepository.kt
│   │   ├── SpeechRecognizerWrapper.kt
│   │   └── SpeechState.kt  # Sealed interface representing speech input states
│   └── usecase/            # Use cases encapsulating business logic
│       ├── EvaluateNotificationUseCase.kt # Matches notifications against active rules
│       └── ParseCommandUseCase.kt         # Feeds natural language prompt to AIEngine
│
├── data/                   # Data Access & Concrete Implementations
│   ├── db/                 # Room DB Setup
│   │   ├── HushDatabase.kt
│   │   └── RoomConverters.kt
│   │   ├── dao/            # Room DAOs
│   │   │   ├── NotificationLogDao.kt
│   │   │   └── RuleDao.kt
│   │   └── entity/         # Room Entities
│   │       ├── NotificationLogEntity.kt
│   │       └── RuleEntity.kt
│   ├── pref/               # SharedPreferences
│   │   └── OnboardingPrefs.kt
│   └── repository/         # Data Repositories & Engine Implementations
│       ├── AIEngineImpl.kt # Implements AIEngine via Google AI Client (Gemini Nano)
│       ├── HistoryRepositoryImpl.kt
│       ├── PackageResolverImpl.kt
│       ├── PermissionManagerImpl.kt
│       ├── PromptTemplates.kt
│       ├── RuleRepositoryImpl.kt
│       └── SpeechRecognizerWrapperImpl.kt # Implements SpeechRecognizerWrapper using Android Speech APIs
│
├── service/                # Android Services
│   └── HushNotificationListener.kt # Intercepts notifications using NotificationListenerService
│
└── ui/                     # Presentation Layer (Jetpack Compose UI)
    ├── navigation/         # NavGraph and Routes
    │   ├── HushNavigation.kt
    │   └── ScreenRoute.kt
    ├── screens/            # UI Screens & ViewModels
    │   ├── MainScreen.kt
    │   ├── chat/           # Conversational Chat Screen
    │   │   ├── ChatScreen.kt
    │   │   └── ChatViewModel.kt
    │   ├── history/        # History Logs Screen
    │   │   ├── HistoryScreen.kt
    │   │   └── HistoryViewModel.kt
    │   ├── onboarding/     # Onboarding Setup Screen
    │   │   ├── OnboardingScreen.kt
    │   │   └── OnboardingViewModel.kt
    │   ├── rules/          # Rules List & Details Screen
    │   │   ├── RulesScreen.kt
    │   │   └── RulesViewModel.kt
    │   └── settings/       # Settings/Preferences Screen
    │       ├── SettingsScreen.kt
    │       └── SettingsViewModel.kt
    └── theme/              # Material 3 Theming
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## Build Setup

### Prerequisites

- **JDK 17**
- **Android SDK Platform 35**
- Minimum SDK support: **Android 13 (API level 33)**
- Target SDK support: **Android 15 (API level 35)**

### Dependency Resolution

To support reproducible, isolated builds without requiring external network connections during build-time, Hush resolves all third-party dependencies from a local Maven repository located under the `repo/` directory in the project root.

In `settings.gradle.kts`, the resolution is configured as follows:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("${settingsDir}/repo") }
        google()
        mavenCentral()
    }
}
```

To build the project, run:
```bash
./gradlew assembleDebug
```

---

## Testing Guidelines

### Unit Tests

Unit tests target individual layers and components in isolation using mocks or fakes. Run them via the command line:

```bash
./gradlew testDebugUnitTest
```

#### Test Class Mapping

- **`AIEngineImplTest`**: Verifies local JSON parsing, clean-up operations, time format robust parsing, input validation, and expected exceptions when AI is unavailable.
- **`EvaluateNotificationUseCaseTest`**: Tests rule evaluation logic in detail. Includes boundary conditions (e.g. daytime windows, overnight time windows, package-level filters, exact/regex/contains matching fields, negation / inverted rules) to ensure correct actions (`ALLOW`, `BLOCK`, `MUTE`) are chosen.
- **`ParseCommandUseCaseTest`**: Validates the end-to-end command parsing pipeline, verifying app package name resolution mapping and correct exception throwing for empty or malformed inputs.
- **`ChatViewModelTest`**: Tests presentation logic, conversational state transitions (e.g., idle, typing, recording voice, confirmation dialog display), Hilt module mocks integration, and rule confirmation or cancellation effects.

### Instrumented Tests (E2E)

End-to-End instrumented tests run on physical devices or Android emulators to verify the full user flows, database persistence, and system-level interception.

Run Android instrumented tests with:
```bash
./gradlew connectedAndroidTest
```
