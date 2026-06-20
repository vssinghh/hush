# Scope: Milestone 1 (Project Skeleton)

## Architecture
- Root & app-level Kotlin DSL Gradle build script configuration targeting SDK 35 and min SDK 33.
- Dagger Hilt DI initialization (Hilt application, Hilt gradle plugins, and DI injection points).
- Room DB configuration (Database, entities, and DAOs skeletons, module provider).
- Material 3 / Material You theme configuration (Color.kt, Theme.kt, Type.kt).
- Bottom Navigation bar with four tabs: Chat, Rules, History, Settings.
- Jetpack Compose Navigation Graph routing between onboarding and main dashboard, handling onboarding completion state in SharedPreferences.
- Screens skeletons for Onboarding, Chat, Rules, History, Settings.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | M1.1: Gradle & Hilt setup | Create project structure, configuration files, and Dagger Hilt application class | none | DONE |
| 2 | M1.2: Room DB Skeleton | Setup DB entities, DAO interface, AppDatabase, and Hilt modules | M1.1 | DONE |
| 3 | M1.3: Navigation & UI Theme | Establish navigation graph, Material 3 dynamic theme, bottom navigation tabs | M1.1 | DONE |
| 4 | M1.4: Screen Skeletons | Skeleton compose views for Chat, Rules, History, Settings, and Onboarding screen | M1.3 | DONE |
| 5 | M1.5: Verification | Run gradle build to ensure compilation is 100% clean and correct | M1.1-M1.4 | DONE |

## Interface Contracts
### Main Navigation Route Contract
- Onboarding Screen -> Main Screen (containing Bottom Navigation)
- Bottom Navigation -> [Chat, Rules, History, Settings]
- SharedPreferences key `onboarding_completed` (boolean) to determine launch screen.
