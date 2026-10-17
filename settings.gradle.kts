pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":app", ":data")

// The Android app launches the shopot-core binary built from the checked-out
// `core-src` git submodule. The submodule is intentionally not a Gradle module.
rootProject.name = "Stille-Messenger"
