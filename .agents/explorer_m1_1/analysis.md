# Build Environment Analysis & Gradle DSL Configuration Report — Milestone 1

## Summary
This report analyzes the local build environment, defines a Clean Architecture package layout, and details the recommended root and module-level Kotlin DSL Gradle configurations (with Version Catalog) targeting SDK 35 and min SDK 33.

---

## 1. Build Environment Verification

An audit of the host environment was conducted to identify the availability of JDK, Gradle, and Android SDK command-line tools.

### 1.1 Command Executions & Observations
1. **Java Development Kit (JDK)**:
   - Command: `java -version`
   - Result:
     ```
     The operation couldn’t be completed. Unable to locate a Java Runtime.
     Please visit http://www.java.com for information on installing Java.
     ```
   - System JVM Path check: `/Library/Java/JavaVirtualMachines` was inspected and found to be empty.
   - Status: **JDK is not installed / not configured.**

2. **Android Command-line Tools (`android` CLI / `sdkmanager`)**:
   - Command: `which android` / `which sdkmanager`
   - Result: `android not found` / `sdkmanager not found`.
   - typical paths `/Users/vipinsingh/Library/Android/sdk` were checked and did not exist.
   - Status: **Android SDK and Android CLI are not present in the PATH.**

3. **Gradle Build System**:
   - Command: `which gradle`
   - Result: `gradle not found`.
   - Directory search in `hush/`: No local `gradlew` wrapper scripts exist.
   - Status: **Gradle is not installed / not configured.**

### 1.2 Environment Recommendations
To build and run the Hush project skeleton, the following setup must be performed on the host system:
1. **Install Java JDK 17 (or 21)**: Android Gradle Plugin (AGP) 8.5.0 requires Java 17 or higher. Install a distribution like Temurin or Azul Zulu JDK.
2. **Install Android Command-line Tools & Platform SDK**:
   - Download Command Line Tools from developer.android.com.
   - Set `ANDROID_HOME` pointing to the SDK directory.
   - Install SDK platform 35 and build tools:
     ```bash
     sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
     ```
3. **Generate Gradle Wrapper**: Use a system Gradle to run `gradle wrapper` within the project root to generate the local gradlew executables.

---

## 2. Recommended Directory Structure

To align with Clean Architecture (UI → Domain → Data) and incorporate Dagger Hilt, Room, and Navigation components, we recommend the following folder hierarchy:

```
hush/
├── gradle/
│   └── libs.versions.toml                     # Version Catalog definitions
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/hush/app/
│   │   │   │   ├── HushApp.kt                 # Application class for Hilt DI trigger
│   │   │   │   ├── MainActivity.kt            # Single Activity hosting compose theme/nav
│   │   │   │   │
│   │   │   │   ├── di/                        # Hilt DI Modules
│   │   │   │   │   ├── DatabaseModule.kt      # Provides Room DB, DAOs
│   │   │   │   │   ├── PreferencesModule.kt   # Provides SharedPreferences wrapper
│   │   │   │   │   └── RepositoryModule.kt    # Binds domain repositories to implementations
│   │   │   │   │
│   │   │   │   ├── data/                      # Data Layer (Implementations, local, AI, Speech)
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── HushDatabase.kt    # Room database class
│   │   │   │   │   │   ├── RoomConverters.kt  # Custom TypeConverters for Instant/LocalTime
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── RuleDao.kt
│   │   │   │   │   │   │   └── NotificationLogDao.kt
│   │   │   │   │   │   └── entity/
│   │   │   │   │   │       ├── RuleEntity.kt  # Room Rule table mapping
│   │   │   │   │   │       └── NotificationLogEntity.kt # Room log table mapping
│   │   │   │   │   ├── pref/
│   │   │   │   │   │   └── OnboardingPrefs.kt # SharedPref wrapper for onboarding flag
│   │   │   │   │   └── repository/
│   │   │   │   │       ├── RuleRepositoryImpl.kt
│   │   │   │   │       └── HistoryRepositoryImpl.kt
│   │   │   │   │
│   │   │   │   ├── domain/                    # Domain Layer (Pure Kotlin logic)
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── Rule.kt            # Pure Kotlin rule model
│   │   │   │   │   │   ├── NotificationEvent.kt # Pure Kotlin log event model
│   │   │   │   │   │   ├── RuleAction.kt      # Enum: ALLOW, BLOCK, MUTE
│   │   │   │   │   │   ├── MatchField.kt      # Enum: TITLE, TEXT, SENDER, ANY
│   │   │   │   │   │   └── MatchType.kt       # Enum: CONTAINS, REGEX, EXACT
│   │   │   │   │   ├── repository/
│   │   │   │   │   │   ├── RuleRepository.kt  # Repository Interfaces
│   │   │   │   │   │   └── HistoryRepository.kt
│   │   │   │   │   └── usecase/
│   │   │   │   │       ├── ParseCommandUseCase.kt
│   │   │   │   │       ├── EvaluateNotificationUseCase.kt
│   │   │   │   │       ├── ManageRulesUseCase.kt
│   │   │   │   │       └── QueryHistoryUseCase.kt
│   │   │   │   │
│   │   │   │   ├── service/                   # Background Services
│   │   │   │   │   └── HushNotificationListener.kt # Intercepts notifications
│   │   │   │   │
│   │   │   │   └── ui/                        # Presentation Layer (Jetpack Compose UI)
│   │   │   │       ├── components/            # Reusable UI widgets (RuleCard, VoiceButton)
│   │   │   │       ├── navigation/
│   │   │   │       │   ├── ScreenRoute.kt     # Route mapping and BottomTab contracts
│   │   │   │       │   └── HushNavigation.kt  # NavHost & onboarding navigation rules
│   │   │   │       ├── theme/                 # Material 3 & dynamic theming definitions
│   │   │   │       │   ├── Color.kt
│   │   │   │       │   ├── Theme.kt
│   │   │   │       │   └── Type.kt
│   │   │   │       └── screens/
│   │   │   │           ├── MainScreen.kt      # Tab controller & Bottom navigation UI
│   │   │   │           ├── chat/
│   │   │   │           │   ├── ChatScreen.kt
│   │   │   │           │   └── ChatViewModel.kt
│   │   │   │           ├── history/
│   │   │   │           │   ├── HistoryScreen.kt
│   │   │   │           │   └── HistoryViewModel.kt
│   │   │   │           ├── onboarding/
│   │   │   │           │   └── OnboardingScreen.kt
│   │   │   │           ├── rules/
│   │   │   │           │   ├── RulesScreen.kt
│   │   │   │           │   └── RulesViewModel.kt
│   │   │   │           └── settings/
│   │   │   │               ├── SettingsScreen.kt
│   │   │   │               └── SettingsViewModel.kt
│   │   │   └── res/
│   │   └── androidTest/                       # Instrumented test suite
│   │       └── java/com/hush/app/
│   │           └── data/db/
│   │               └── HushDatabaseTest.kt    # Tests database CRUD & Room structure
│   └── build.gradle.kts                       # Module-level Gradle config
├── settings.gradle.kts                        # Settings script
├── build.gradle.kts                            # Root-level Gradle config
└── gradle.properties                          # Project properties
```

---

## 3. Recommended Gradle Configurations

### 3.1 `gradle/libs.versions.toml`
The version catalog declares central versions and library bundles, ensuring Kotlin 2.0 compiler compatibility and using KSP for Room.

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
ksp = "2.0.0-1.0.21"
hilt = "2.51.1"
room = "2.6.1"
androidx-core = "1.13.1"
androidx-lifecycle = "2.8.2"
activity-compose = "1.9.0"
compose-bom = "2024.06.00"
navigation-compose = "2.7.7"
hilt-navigation-compose = "1.2.0"
coroutines = "1.8.1"
junit = "4.13.2"
androidx-test-ext = "1.1.5"
espresso-core = "3.5.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidx-core" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "androidx-lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }

# Coroutines
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso-core" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### 3.2 `settings.gradle.kts`
Initializes dependency resolution rules to utilize the Version Catalog.

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Hush"
include(":app")
```

### 3.3 Root-level `build.gradle.kts`
Declares the root plugins that submodules can apply.

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}
```

### 3.4 Module-level `app/build.gradle.kts`
Declares the build specifications for the app targeting Android 15 (SDK 35), and lists dependencies for Hilt, Room, Compose, and testing.

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.hush.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hush.app"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Schema output directory config for Room
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation-compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Local Unit Tests
    testImplementation(libs.junit)
    
    // Instrumented Tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso-core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}
```

### 3.5 `gradle.properties`
Configures build caching, AndroidX namespace mapping, and memory limits.

```properties
# Enable AndroidX and automatic jetifier mappings if legacy libraries are encountered
android.useAndroidX=true
android.enableJetifier=false

# Non-transitive R classes speed up build times
android.nonTransitiveRClass=true

# Optimal Gradle Daemon memory limits
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m

# Kotlin code formatting guidelines
kotlin.code.style=official
```

---

## 4. Gradle Setup Integration & Architectural Alignment

### 4.1 Bridging Dagger Hilt & Kotlin DSL
The module `app/build.gradle.kts` applies the Hilt gradle plugin via `alias(libs.plugins.hilt.android)`. The compilation runtime and code generation are separated:
- Compiler is mapped via `ksp(libs.hilt.compiler)` to execute code generation during compilation.
- The runtime is mapped via `implementation(libs.hilt.android)` to expose the DI classes (e.g. `@HiltAndroidApp`, `@Module`, `@Inject`) in code.

### 4.2 Room DB with Kotlin Symbol Processing (KSP)
Rather than relying on legacy kapt, KSP is utilized for Room annotation processing. KSP aligns with Kotlin 2.0 compilers and performs up to 2x faster than kapt.
The directory for schema exports is specified under `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` which instructs Room to verify changes in schemas during builds, providing protection against destructive model modifications.

### 4.3 Compose Navigation & Theming
By setting `compileSdk = 35` and `minSdk = 33`, the layout is dynamic-color friendly. Dynamic dynamic Schemes are provided at the platform level (since Android 12).
The Kotlin Gradle plugin version `2.0.0` uses the new first-class Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`), removing the old Compose Compiler dependency specification requirement in `app/build.gradle.kts` and ensuring 100% build-time compatibility with Kotlin features.
