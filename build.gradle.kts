import io.gitlab.arturbosch.detekt.Detekt
import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 built-in Kotlin defaults to KGP 2.2.10; pin the catalog Kotlin version.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dependencyGuard) apply false
}

fun Project.ktlintBaselineFile() =
    rootProject.layout.projectDirectory
        .file(
            if (this == rootProject) {
                "config/ktlint/baseline.xml"
            } else {
                "config/ktlint/${path.removePrefix(":").replace(':', '-')}-baseline.xml"
            },
        ).asFile

fun Project.configureKtlint() {
    extensions.configure<KtlintExtension>("ktlint") {
        android.set(true)
        outputToConsole.set(true)
        baseline.set(ktlintBaselineFile())
        filter {
            exclude("**/build/**", "**/generated/**")
        }
    }
}

configureKtlint()

subprojects {
    if (buildFile.isFile) {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        configureKtlint()
    }
}

dependencies {
    // Merged coverage for max-coverage gate (see docs/TESTING.md).
    kover(projects.core.catalog)
    kover(projects.core.domain)
    kover(projects.feature.home)
    kover(projects.core.analytics)
    kover(projects.core.rss)
    kover(projects.core.downloads)
    kover(projects.core.playback)
    kover(projects.core.ranking)
    kover(projects.core.prefs)
    kover(projects.core.network)
    kover(projects.core.database)
    kover(projects.core.model)
    kover(projects.feature.info)
    kover(projects.feature.explore)
    kover(projects.feature.library)
    kover(projects.feature.onboarding)
    kover(projects.feature.briefing)
    kover(projects.feature.player)
    kover(projects.app)
}

kover {
    // Root has no Android sources; map each dependency's debug into a shared report variant.
    currentProject {
        createVariant("merged") {
            add("debug", optional = true)
        }
    }
    reports {
        filters {
            excludes {
                // Generated / Android glue — keep the gate focused on app logic.
                androidGeneratedClasses()
                classes(
                    "*.BuildConfig",
                    "*.R",
                    "*.R$*",
                    "*.databinding.*",
                    // Irreducible Media3 / Auto orchestration — covered by policy tests + Maestro.
                    "cx.aswin.boxlore.core.playback.PlaybackRepository",
                    "cx.aswin.boxlore.core.playback.PlaybackRepository$*",
                    "cx.aswin.boxlore.core.playback.service.*",
                    "cx.aswin.boxlore.core.playback.service.auto.*",
                    // Application / Activity glue — covered by instrumented / Maestro.
                    "cx.aswin.boxlore.BoxLoreApplication",
                    "cx.aswin.boxlore.BoxLoreApplication$*",
                    "cx.aswin.boxlore.MainActivity",
                    "cx.aswin.boxlore.MainActivity$*",
                    // :app Compose nav / FCM / survey chrome — covered by androidTest + Maestro.
                    "cx.aswin.boxlore.navigation.*",
                    "cx.aswin.boxlore.ui.*",
                    "cx.aswin.boxlore.fcm.*",
                    "cx.aswin.boxlore.surveys.*",
                )
                annotatedBy(
                    "androidx.compose.runtime.Composable",
                    "androidx.compose.ui.tooling.preview.Preview",
                )
            }
        }
        variant("merged") {
            verify {
                // Max-coverage ratchet: 40 → 45 → 55 → 70 → 80 (see docs/TESTING.md).
                // Measured ≈47.9% with current hermetic suites; lock 45 with headroom.
                rule("Merged line coverage (max-coverage gated modules)") {
                    minBound(45)
                }
            }
        }
    }
}

val detektSourceDirs =
    subprojects
        .flatMap { subproject ->
            listOf(
                "src/main/java",
                "src/main/kotlin",
                "src/test/java",
                "src/test/kotlin",
                "src/androidTest/java",
                "src/androidTest/kotlin",
            ).map { subproject.file(it) }
        }.filter { it.exists() }

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(detektSourceDirs)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    include("**/*.kt")
    exclude("**/build/**", "**/generated/**")
    reports {
        xml.required.set(true)
        html.required.set(true)
        txt.required.set(false)
        sarif.required.set(true)
        md.required.set(false)
    }
}
