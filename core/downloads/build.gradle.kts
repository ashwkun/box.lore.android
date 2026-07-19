plugins {
    alias(libs.plugins.androidLibrary)
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
    namespace = "cx.aswin.boxlore.core.downloads"
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

    lint {
        disable += "NullSafeMutableLiveData"
        abortOnError = false
        checkReleaseBuilds = true
    }

    testOptions {
        // Robolectric + WorkManager testing + DataStore prefs for worker unit tests (B3).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    // `:core:catalog` provides PodcastRepository, SubscriptionRepository,
    // UserPreferencesRepository, BoxLoreDatabase (via :core:database api), domain ports, etc.
    api(projects.core.catalog)
    implementation(projects.core.database)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    // RankingFeedbackRepository / AdaptiveCandidateScorer — declare directly (catalog no longer api-exports ranking).
    implementation(projects.core.ranking)
    implementation(projects.core.analytics)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    // Media3 offline / cache (DownloadManager, DownloadRepository, ThrottlingDataSource)
    implementation(libs.androidx.media3.exoplayer)

    // WorkManager (SmartDownloadWorker, AutoDownloadWorker, PurgeSmartDownloadsWorker)
    implementation(libs.androidx.work.runtime)

    // Testing
    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.work:work-testing:${libs.versions.work.get()}")
}
