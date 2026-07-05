# Contributing to Hush

First off, thank you for taking the time to contribute! Contributions from the community help make Hush better for everyone.

Here is a guide to help you get started with contributing.

---

## How Can I Contribute?

### 1. Reporting Bugs
* Check the [Issues](https://github.com/vssinghh/hush/issues) tab to see if the bug has already been reported.
* If not, open a new issue using the **Bug Report** template.
* Include clear steps to reproduce the issue, and if possible, logs (logcat) or screenshots.

### 2. Suggesting Features
* Open an issue using the **Feature Request** template.
* Explain the feature's value and how it fits Hush's privacy-first on-device AI model.

### 3. Submitting Pull Requests
* Fork the repository and create your branch from `main`.
* If you're fixing a bug or adding a feature, please link it to an existing open issue.
* Keep your commits focused and write descriptive commit messages.
* Ensure all tests pass before submitting.

---

## Development Setup

### Prerequisites
* **JDK 17**
* **Android SDK Platform 35**
* A device supporting **Gemini Nano** (Pixel 6+ or similar with AICore initialized).

### Building the Project
You can build the project from the command line:

```bash
# Clone your fork
git clone https://github.com/YOUR-USERNAME/hush.git
cd hush

# Build debug APK
./gradlew assembleDebug
```

### Running Tests
Make sure unit tests pass before you submit code:

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

---

## Code Style & Architecture
Hush follows **Clean Architecture** principles:
* **Domain Layer**: Contains use cases and pure interfaces. No Android dependencies.
* **Data Layer**: Room DB, Gemini Nano integration, and repository implementations.
* **UI Layer**: Jetpack Compose and ViewModels.

Please follow standard Kotlin coding conventions and formatting. Keep architecture boundaries clean (e.g. do not leak UI/Android components into the Domain layer).

Thank you for supporting open source!
