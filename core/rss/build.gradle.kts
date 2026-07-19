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
    namespace = "cx.aswin.boxlore.core.rss"
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
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp)
    // Real XmlPullParser implementation backing the android.util.Xml test stub so the
    // custom RSS parser can be exercised hermetically off-device.
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}

// rssparser pulls OkHttp 5.x; MockWebServer 4.12 needs OkHttp 4 internals.
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
