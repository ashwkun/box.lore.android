plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kover)
    alias(libs.plugins.roborazzi)
}

kover {
    currentProject {
        createVariant("merged") {
            add("debug")
        }
    }
}

android {
    namespace = "cx.aswin.boxlore.feature.home"
    // compileSdk 36: androidTest pulls activity-compose / core 1.18 AAR metadata
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
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
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    packaging {
        resources {
            // okhttp-coroutines + jspecify both ship this OSGi manifest path
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(projects.core.catalog)
    implementation(projects.core.downloads)
    implementation(projects.core.playback)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation("org.mockito:mockito-core:5.14.2")
    debugImplementation(libs.androidx.ui.test.manifest)

    // P24 Compose UI tests (JUnit4 + AndroidJUnitRunner; CI via android-instrumented-tests.yml)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.ui.test.manifest)
}
