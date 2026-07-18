import java.util.Properties

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
    namespace = "cx.aswin.boxlore.core.data"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { localProps.load(it) }
    }

    defaultConfig {
        minSdk = 31
        buildConfigField("String", "BOXCAST_API_BASE_URL", "\"${localProps.getProperty("BOXCAST_API_BASE_URL", "")}\"")
        buildConfigField("String", "BOXCAST_PUBLIC_KEY", "\"${localProps.getProperty("BOXCAST_PUBLIC_KEY", "")}\"")
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
        // Catalog MockWebServer tests use Mockito Context/DB doubles (no Robolectric Room).
        // Keep false: ?attr theme refs in dependency resources historically break unit-test AAPT;
        // drawables in this module use concrete tints as a belt-and-suspenders fix.
        unitTests.isIncludeAndroidResources = false
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.network)
    api(projects.core.domain)
    api(projects.core.database)
    api(projects.core.prefs)
    implementation(libs.androidx.core.ktx)
    // RSS layer: RssFeedClient, RssPodcastRepository, RssIdGenerator, RssSourceMatcher,
    // DownloadCacheRelinker port — api-exported so features/app that depend on :core:data
    // see all RSS types without adding a direct :core:rss dependency.
    api(projects.core.rss)

    implementation(libs.retrofit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.datastore.preferences)

    // JSON Streaming
    implementation(libs.gson)
    implementation(libs.okhttp)
    // Firebase (database and messaging — SubscriptionRepository uses firebase.database + messaging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)

    // Analytics (PostHog lives in :core:analytics; data re-exports it so existing imports keep working)
    api(projects.core.analytics)

    // Ranking (AdaptiveCandidateScorer, RankingFeedbackRepository, AdaptiveRankingRepository, etc.)
    // api-exported so features/downloads/playback that depend on data see ranking types transitively.
    api(projects.core.ranking)

    // Install Referrer
    implementation("com.android.installreferrer:installreferrer:2.2")

    // Testing (JUnit 5 + vintage for migration; no MockK)
    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.6.1")
    // Hermetic Context / Room doubles for catalog MockWebServer tests (no MockK).
    testImplementation("org.mockito:mockito-core:5.14.2")
}

// rssparser / PostHog pull OkHttp 5.x; MockWebServer 4.12 needs OkHttp 4 internals
// (okhttp3.internal.Util). Pin the unit-test classpath to 4.12 like :core:network.
configurations
    .matching { it.name.contains("UnitTest", ignoreCase = true) }
    .configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.squareup.okhttp3" &&
                (requested.name == "okhttp" || requested.name == "okhttp-android")
            ) {
                useVersion("4.12.0")
                because("Align MockWebServer 4.12 with OkHttp 4.x on JVM unit tests")
            }
            if (requested.group == "com.squareup.okhttp3" && requested.name == "okhttp-coroutines") {
                useTarget("com.squareup.okhttp3:okhttp:4.12.0")
                because("Drop OkHttp 5 coroutines artifact from unit-test classpath")
            }
        }
    }
