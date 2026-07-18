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
    // Merged coverage for modest verify gate (see docs/TESTING.md).
    kover(projects.core.catalog)
    kover(projects.core.domain)
    kover(projects.feature.home)
    kover(projects.core.analytics)
    kover(projects.core.rss)
    kover(projects.core.downloads)
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
                )
                annotatedBy(
                    "androidx.compose.runtime.Composable",
                    "androidx.compose.ui.tooling.preview.Preview",
                )
            }
        }
        variant("merged") {
            verify {
                // Coverage ratchet path: 8 → 10 → 12 → 15 → 25 (see docs/TESTING.md).
                // Soft future gates may add module-specific floors for ranking/downloads.
                rule("Modest line coverage (data/domain/home + analytics/rss/downloads)") {
                    // Coverage ratchet: 8 → 10 → 12 → 15 → 25 (see docs/TESTING.md).
                    minBound(15)
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
