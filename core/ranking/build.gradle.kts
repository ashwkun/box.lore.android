plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
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
    namespace = "cx.aswin.boxlore.core.ranking"
    compileSdk = 36

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
    // Model types (Episode, Podcast, PodcastGenres, RankingAggregateTelemetry)
    implementation(projects.core.model)

    // PodcastScoring / ScorablePodcast live in :core:database (package cx.aswin.boxlore.core.database)
    // ListeningHistoryEntity also lives there.
    implementation(projects.core.database)

    // RankingResetPort
    implementation(projects.core.domain)

    // BoxcastPrefs (LearningEventLog references PREFS_NAME / KEY_LEARNER_LOG_ENABLED)
    implementation(projects.core.prefs)

    // AdaptiveRankingDatabase — ranking's own private Room DB
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Testing (JUnit 5 + vintage; no MockK)
    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.gson)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.6.1")
}
