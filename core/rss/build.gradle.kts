plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kover)
}

kover {
    currentProject {
        createVariant("merged") {
            add("debug")
        }
    }
}

android {
    namespace = "cx.aswin.boxlore.core.rss"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // Model types (Episode, Podcast, Person, Transcript)
    implementation(projects.core.model)

    // Room DB, entities (PodcastEntity, RssEpisodeEntity, …) — api so callers see entity types
    // returned by RssPodcastRepository.getPodcast() without a second direct dependency.
    api(projects.core.database)

    // RssSubscriptionPort, RssSubscriptionResult — api so callers can implement/consume the port
    // without also depending on :core:domain directly.
    api(projects.core.domain)

    // HTTP fetch + OkHttp conditional HEAD checks
    implementation(libs.okhttp)

    // RSS / Atom feed parsing
    implementation(libs.rss.parser)

    // Firebase Cloud Messaging — unsubscribeFromTopic on Podcast Index link migration
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.kotlinx.coroutines.android)

    // Testing (JUnit 5 + vintage; no MockK)
    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}
