import java.util.Properties

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cx.aswin.boxlore.core.data"
    compileSdk = 35

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
        unitTests.isIncludeAndroidResources = false
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.network)
    api(projects.core.database)
    implementation(libs.androidx.core.ktx)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.datastore.preferences)

    // Room (ksp retained for ranking AdaptiveRankingDatabase; runtime via :core:database)
    ksp(libs.androidx.room.compiler)

    // JSON Streaming
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.rss.parser)
    // Media3 offline / cache (download manager); player session lives in :core:playback
    implementation(libs.androidx.media3.exoplayer)
    
    // Firebase (database and messaging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)

    // PostHog
    implementation(libs.posthog.android)

    // Install Referrer
    implementation("com.android.installreferrer:installreferrer:2.2")

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Testing (JUnit 5 + vintage for migration; no MockK)
    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.6.1")
}
