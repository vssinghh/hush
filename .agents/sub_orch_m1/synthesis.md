# Synthesized Project Skeleton Architecture & Implementation Plan — Remediation Iteration 3

## 1. Executive Summary (Conclusion First)
This document outlines the consolidated, unified implementation plan for the Hush Android app Milestone 1 Project Skeleton, resolving all issues from the Gen 2 Forensic Audit and Reviewer reports. 

The core issues and their resolution strategies are:
1. **Mock Permission Bypass in Onboarding**: Abstracted behind a Hilt-injectable `PermissionManager` interface, using `PermissionManagerImpl` in production and a `FakePermissionManager` in instrumented tests. Managed via `OnboardingViewModel`.
2. **Mock database evaluation shortcut in tests**: Replaced the local rule-matching duplication inside `RealWorldScenarioE2ETest.kt` with a direct inject and call to the production `EvaluateNotificationUseCase.execute(...)`.
3. **Missing Time Window Evaluation**: Implemented boundary-inclusive daytime and overnight (cross-midnight) time window comparisons inside `EvaluateNotificationUseCase.kt`.
4. **Fake Espresso Intents namespace**: Declared the official `androidx.test.espresso:espresso-intents` library dependency in `app/build.gradle.kts` and deleted the local stub classes.
5. **Theme Option Facade**: Created a `MainViewModel` observing SharedPreferences key `"theme_option"` to dynamically push appearance updates to the Compose hierarchy in `MainActivity.kt`.
6. **Logging Discrepancy & Test Assertions**: Adopted a rule-matching-only logging strategy (storing history only when an explicit rule fires) to prevent database bloat and respect user privacy. Assertions in E2E tests have been updated accordingly.
7. **Thread-Safety Test Synchronicity**: Refactored `testInterception_RapidConcurrentNotifications_ThreadSafety` in `NotificationInterceptionE2ETest` to launch 30 parallel coroutines on `Dispatchers.Default` using `async` and `jobs.awaitAll()`.
8. **Prop-Drilling Refactoring**: Removed repositories and wrapper dependencies from `MainActivity.kt` param signatures, injecting them via `@HiltViewModel` (`hiltViewModel()`) directly into the target screen Composables.

---

## 2. Directory Structure & File Inventory
The following files will be created or modified:

```
hush/
├── gradle/
│   └── libs.versions.toml                     # Centralized version catalog
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/hush/app/
│   │   │   │   ├── HushApp.kt                 # Dagger Hilt Application
│   │   │   │   ├── MainActivity.kt            # Single Activity setting up compose theme/nav
│   │   │   │   ├── MainViewModel.kt           # Central ViewModel (theme observation)
│   │   │   │   │
│   │   │   │   ├── data/                      # Persistence & Repositories
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── HushDatabase.kt    # Room Database (exportSchema = true)
│   │   │   │   │   │   ├── RoomConverters.kt  # LocalTime/Instant TypeConverters
│   │   │   │   │   │   └── dao/
│   │   │   │   │   │       ├── RuleDao.kt
│   │   │   │   │   │       └── NotificationLogDao.kt
│   │   │   │   │   ├── pref/
│   │   │   │   │   │   └── OnboardingPrefs.kt # SharedPreferences wrapper
│   │   │   │   │   └── repository/            # Repo implementations
│   │   │   │   │       ├── RuleRepositoryImpl.kt
│   │   │   │   │       ├── HistoryRepositoryImpl.kt
│   │   │   │   │       └── PermissionManagerImpl.kt # Production permission manager
│   │   │   │   │
│   │   │   │   ├── di/                        # Hilt DI modules
│   │   │   │   │   ├── DatabaseModule.kt      # Room DB providers
│   │   │   │   │   ├── PreferencesModule.kt   # Preference providers
│   │   │   │   │   ├── RepositoryModule.kt    # Repository interface binds
│   │   │   │   │   └── PermissionModule.kt    # Permission manager binds
│   │   │   │   │
│   │   │   │   ├── domain/                    # Clean domain logic
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── Rule.kt            # Domain Rule model
│   │   │   │   │   │   ├── NotificationEvent.kt # Domain History model
│   │   │   │   │   │   ├── RuleAction.kt      # Enums: ALLOW, BLOCK, MUTE
│   │   │   │   │   │   ├── MatchField.kt      # Enums: TITLE, TEXT, SENDER, ANY
│   │   │   │   │   │   └── MatchType.kt       # Enums: CONTAINS, REGEX, EXACT
│   │   │   │   │   ├── permission/
│   │   │   │   │   │   └── PermissionManager.kt # Permission interface
│   │   │   │   │   └── usecase/
│   │   │   │   │       └── EvaluateNotificationUseCase.kt # Rule evaluation logic
│   │   │   │   │
│   │   │   │   └── ui/                        # Presentation layer
│   │   │   │       ├── navigation/
│   │   │   │       │   ├── ScreenRoute.kt     # Bottom tabs & Root routes
│   │   │   │       │   └── HushNavigation.kt  # Root NavHost
│   │   │   │       ├── theme/                 # Styling
│   │   │   │       │   ├── Color.kt
│   │   │   │       │   ├── Theme.kt
│   │   │   │       │   └── Type.kt
│   │   │   │       └── screens/
│   │   │   │           ├── MainScreen.kt      # Tab controller & nested NavHost
│   │   │   │           ├── chat/
│   │   │   │           │   ├── ChatScreen.kt  # Conversational assistant
│   │   │   │           │   └── ChatViewModel.kt # Chat ViewModel
│   │   │   │           ├── history/
│   │   │   │           │   └── HistoryScreen.kt # Logs view
│   │   │   │           ├── onboarding/
│   │   │   │           │   ├── OnboardingScreen.kt # Perm/Setup guide
│   │   │   │           │   └── OnboardingViewModel.kt # Onboarding ViewModel
│   │   │   │           ├── rules/
│   │   │   │           │   └── RulesScreen.kt # Custom filters list
│   │   │   │           └── settings/
│   │   │   │               └── SettingsScreen.kt # Configuration options
│   │   │   │
│   │   │   └── res/                           # Resources
│   │   │       ├── values/strings.xml
│   │   │       └── drawable/ic_hush.xml
│   │   │
│   │   └── androidTest/                       # Instrumented Test Suites
│   │       └── java/com/hush/app/
│   │           ├── e2e/
│   │           │   ├── NotificationInterceptionE2ETest.kt
│   │           │   └── RealWorldScenarioE2ETest.kt
│   │           ├── mock/
│   │           │   └── FakePermissionManager.kt # Test permission stub
│   │           └── di/
│   │               └── TestPermissionModule.kt # Test module bindings
│   │
│   ├── build.gradle.kts                       # Module Gradle configuration
│   └── AndroidManifest.xml                    # App configuration manifest
```

---

## 3. Configuration & Dependency Targets
- **Target SDK**: `35`
- **Min SDK**: `33`
- **Kotlin version**: `2.0.0`
- **Dagger Hilt**: `2.51.1`
- **Room version**: `2.6.1`
- **KSP plugin**: `2.0.0-1.0.21`
- **Espresso Intents**: `libs.androidx.espresso.intents` (`androidx.test.espresso:espresso-intents`) added to `app/build.gradle.kts`.
- **Database Schema**: Exporting enabled (`exportSchema = true`), writing to `app/schemas` directory.
