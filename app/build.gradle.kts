import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.dependencyGuard)
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
    namespace = "cx.aswin.boxlore"
    compileSdk = 36

    // Load local.properties globally for the android block
    val localPropsFile = rootProject.file("local.properties")
    val localProps = Properties()
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { localProps.load(it) }
    }

    fun Properties.dual(
        newKey: String,
        oldKey: String,
    ): String {
        val newest = getProperty(newKey)?.trim().orEmpty()
        if (newest.isNotEmpty()) return newest
        return getProperty(oldKey, "") ?: ""
    }

    val resolvedApiBaseUrl = localProps.dual("BOXLORE_API_BASE_URL", "BOXCAST_API_BASE_URL")
    val resolvedPublicKey = localProps.dual("BOXLORE_PUBLIC_KEY", "BOXCAST_PUBLIC_KEY")

    defaultConfig {
        applicationId = "cx.aswin.boxlore"
        minSdk = 31
        targetSdk = 36
        versionCode = 11
        versionName = "0.0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Prefer BOXLORE_* local.properties keys; fall back to BOXCAST_* for existing setups.
        buildConfigField("String", "BOXLORE_API_BASE_URL", "\"$resolvedApiBaseUrl\"")
        buildConfigField("String", "BOXLORE_PUBLIC_KEY", "\"$resolvedPublicKey\"")
        buildConfigField("String", "BOXCAST_API_BASE_URL", "\"$resolvedApiBaseUrl\"")
        buildConfigField("String", "BOXCAST_PUBLIC_KEY", "\"$resolvedPublicKey\"")
        buildConfigField("String", "POSTHOG_API_KEY", "\"${localProps.getProperty("posthog.apiKey", "")}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${localProps.getProperty("posthog.host", "")}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = localProps.getProperty("KEY_STORE_PASSWORD")
            keyAlias = "upload"
            keyPassword = localProps.getProperty("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            // isShrinkResources = true // Cannot shrink resources without code shrinking (minify enabled)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        disable += "NullSafeMutableLiveData"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencyGuard {
    configuration("releaseRuntimeClasspath")
}

dependencies {
    // Core Modules
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.catalog)
    implementation(projects.core.downloads)
    implementation(projects.core.playback)
    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.analytics)
    implementation(projects.core.ranking)
    implementation(projects.core.rss)
    implementation(projects.feature.home)
    implementation(project(":feature:player"))
    implementation(project(":feature:info"))
    implementation(project(":feature:explore"))
    implementation(project(":feature:library"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:briefing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.google.material)

    // Expressive additions
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.palette.ktx)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Image Loading
    implementation(libs.coil.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)

    // Firebase
    implementation(platform(libs.firebase.bom))

    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.database)
    implementation(libs.firebase.appcheck.playintegrity)
    // Debug provider is guarded by BuildConfig.DEBUG at runtime; only debug
    // tokens registered in Firebase console can ever attest through it
    implementation(libs.firebase.appcheck.debug)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // PostHog
    implementation(libs.posthog.android)

    // Play Core
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Tests
    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
