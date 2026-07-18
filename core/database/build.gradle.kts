plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
}

android {
    namespace = "cx.aswin.boxlore.core.database"
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
        // Attempted for in-memory Room DAO tests (B4). See README Testing notes —
        // AAR metadata / schema packaging can still block hermetic Robolectric Room.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    api(projects.core.model)
    api(projects.core.network)

    // Room
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // TypeConverters (Converters.kt)
    implementation(libs.gson)

    // Testing (B4 — in-memory Room DAO when feasible)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.6.1")
}
