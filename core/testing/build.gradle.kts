plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "cx.aswin.boxlore.core.testing"
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
        unitTests.all {
            it.useJUnitPlatform()
            // Konsist / filesystem architecture guards need the repo root.
            it.workingDir = rootProject.projectDir
            it.systemProperty("boxlore.projectRoot", rootProject.projectDir.absolutePath)
        }
    }
}

dependencies {
    api(projects.core.model)
    api(libs.junit.jupiter)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.okhttp.mockwebserver)

    implementation(libs.androidx.core.ktx)

    testImplementation(libs.konsist)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
