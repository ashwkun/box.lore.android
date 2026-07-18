plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "cx.aswin.boxlore.core.playback"
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
    api(projects.core.model)
    api(projects.core.network)
    api(projects.core.database)
    api(projects.core.data)
    implementation(projects.core.downloads)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)

    // Media3 (player + Android Auto session + download service)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)

    // Artwork for session / Auto collages
    implementation(libs.coil)
    implementation(libs.androidx.palette.ktx)

    implementation(libs.gson)
    implementation(libs.okhttp)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}
