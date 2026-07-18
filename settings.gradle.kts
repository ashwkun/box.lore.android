pluginManagement {
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            // Kotlin 2.4 metadata requires R8 9.1.29 or newer.
            classpath("com.android.tools:r8:9.1.31")
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("com.gradle.develocity") version "4.5.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

val isCi =
    !System.getenv("CI").isNullOrEmpty() ||
        !System.getenv("GITHUB_ACTIONS").isNullOrEmpty()

develocity {
    buildScan {
        // Required to publish to the public Develocity host without an interactive prompt.
        // Local builds stay on-demand (`./gradlew <task> --scan`); CI publishes every run.
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")

        // CI agents exit as soon as Gradle returns; finish the upload before that.
        uploadInBackground.set(!isCi)

        if (isCi) {
            publishing.onlyIf { true }
            tag("CI")
            System.getenv("GITHUB_WORKFLOW")
                ?.takeIf { it.isNotEmpty() }
                ?.let { value("GitHub workflow", it) }
            System.getenv("GITHUB_RUN_ID")
                ?.takeIf { it.isNotEmpty() }
                ?.let { value("GitHub run id", it) }
            System.getenv("GITHUB_REF_NAME")
                ?.takeIf { it.isNotEmpty() }
                ?.let { value("GitHub ref", it) }
            System.getenv("GITHUB_SHA")
                ?.takeIf { it.isNotEmpty() }
                ?.let { value("GitHub SHA", it.take(12)) }
            val repository = System.getenv("GITHUB_REPOSITORY")
            if (!repository.isNullOrEmpty()) {
                link("GitHub repository", "https://github.com/$repository")
                val runId = System.getenv("GITHUB_RUN_ID")
                if (!runId.isNullOrEmpty()) {
                    link(
                        "GitHub Actions run",
                        "https://github.com/$repository/actions/runs/$runId",
                    )
                }
            }
        } else {
            // Keep everyday local builds private unless --scan is passed explicitly.
            publishing.onlyIf { false }
            tag("Local")
        }
    }
}

/**
 * Patched versions forced onto the Gradle plugin / buildscript classpath.
 * Keep in sync with open Dependabot Maven alerts attributed to settings.gradle.kts.
 * Build-time only — these do not ship in the APK.
 */
val securityPinnedClasspath =
    run {
        val netty = "4.1.136.Final"
        arrayOf(
            "io.netty:netty-common:$netty",
            "io.netty:netty-buffer:$netty",
            "io.netty:netty-transport:$netty",
            "io.netty:netty-resolver:$netty",
            "io.netty:netty-codec:$netty",
            "io.netty:netty-codec-http:$netty",
            "io.netty:netty-codec-http2:$netty",
            "io.netty:netty-codec-socks:$netty",
            "io.netty:netty-handler:$netty",
            "io.netty:netty-handler-proxy:$netty",
            "io.netty:netty-transport-native-unix-common:$netty",
            "org.bouncycastle:bcprov-jdk18on:1.84",
            "org.bouncycastle:bcpkix-jdk18on:1.84",
            "org.bouncycastle:bcutil-jdk18on:1.84",
            "org.apache.commons:commons-compress:1.26.2",
            "org.bitbucket.b_c:jose4j:0.9.6",
            "org.jdom:jdom2:2.0.6.1",
            "com.google.protobuf:protobuf-java:3.25.5",
            "com.google.protobuf:protobuf-kotlin:3.25.5",
        )
    }

// AGP pulls older Netty / BouncyCastle / etc. Pin patched transitives without a major AGP bump.
gradle.beforeProject {
    buildscript.configurations.findByName("classpath")?.resolutionStrategy {
        force(*securityPinnedClasspath)
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "boxlore"
include(":app")
include(":core:network")
include(":core:data")
include(":core:model")
include(":core:designsystem")
include(":feature:home")
include(":feature:player")
include(":feature:info")
include(":feature:explore")
include(":feature:library")
include(":feature:onboarding")
include(":feature:briefing")
