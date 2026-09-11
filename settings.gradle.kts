pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "LogicLabs"
include(":core-digital")
include(":core-bridge")
include(":core-data")
include(":core-designsystem")
include(":feature-breadboard")
include(":feature-instruments")
include(":feature-tools")
include(":hardware-hal")
include(":core-testing")
include(":app")
