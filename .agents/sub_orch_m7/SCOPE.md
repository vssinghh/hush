# Scope: Milestone 7 (Release Prep)

## Architecture
Milestone 7 packages the application for release. It includes:
- **CI/CD Pipeline**: GitHub Actions CI workflow to build and test the project automatically on pushes/pull requests.
- **Documentation**: Comprehensive `README.md` documenting features, clean architecture layout, setup guide, testing instructions, and validation details.
- **Licensing**: MIT License declaration in `LICENSE`.
- **Release Compilation**: Gradle release configuration for compilation of release APKs.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| 1 | License & CI Setup | Create the `LICENSE` (MIT) and configure the `.github/workflows/build.yml` file | none | DONE |
| 2 | Documentation Creation | Create a detailed `README.md` at project root with screenshots placeholders, architecture, and testing guides | M1 | DONE |
| 3 | Release Signing Config | Configure the `app/build.gradle.kts` release build configs and signing configurations | M2 | DONE |
| 4 | Release Build & Verification | Compile the release APK (`./gradlew assembleRelease`), run all unit tests, and secure Forensic Auditor verification | M3 | DONE |

## Interface Contracts
- **README.md Content**: Must cover:
  1. Description: Privacy-first conversational notification interceptor.
  2. Core features (Conversational creation, notification listener, Room DB rule engine, SpeechRecognizer, material you theme).
  3. Clean architecture layout.
  4. Build and execution setup (JDK 17, Target SDK 35, Min SDK 33).
  5. Verification instructions (Unit and E2E test commands).
- **GitHub Actions build.yml**: Must checkout repository, set up Java 17, and execute `./gradlew testDebugUnitTest` and compile checking.
