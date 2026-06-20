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
        maven { url = uri("${settingsDir}/repo") }
        google()
        mavenCentral()
    }
}

rootProject.name = "Hush"
include(":app")
