pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "boxlore"
include(":app")
include(":core:network")
include(":core:data")
include(":core:model")
include(":core:designsystem")
include(":feature:home")
include(":feature:player")
include(":feature:info")
include(":feature:explore")
include(":feature:library")
include(":feature:onboarding")
include(":feature:briefing")

