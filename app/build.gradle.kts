import java.util.Properties

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

        testInstrumentationRunner = "com.hush.app.runner.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Schema output directory config for Room
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val hasProperties = keystorePropertiesFile.exists()
            val properties = Properties()
            if (hasProperties) {
                keystorePropertiesFile.inputStream().use { properties.load(it) }
            }

            val storeFilePath = System.getenv("RELEASE_STORE_FILE") 
                ?: properties.getProperty("storeFile")
            
            if (!storeFilePath.isNullOrEmpty()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: properties.getProperty("storePassword")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: properties.getProperty("keyAlias")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Fall back to debug signing config if release keystore file is not configured/found
            val releaseConfig = signingConfigs.findByName("release")
            signingConfig = if (releaseConfig != null && releaseConfig.storeFile?.exists() == true) {
                releaseConfig
            } else {
                signingConfigs.getByName("debug")
            }
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

tasks.withType<JavaCompile>().configureEach {
    doFirst {
        val uniqueSources = source.files.distinctBy { it.absolutePath }
        setSource(uniqueSources)
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
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.generativeai)
    implementation(libs.kotlinx.coroutines.play.services)

    // Local Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    
    // Instrumented Tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)

    // Hilt Testing
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
