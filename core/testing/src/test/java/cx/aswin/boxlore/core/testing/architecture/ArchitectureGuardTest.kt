package cx.aswin.boxlore.core.testing.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Architecture-as-code (B8). Runs as `:core:testing` JVM unit tests so
 * `unit-tests.yml` / `./gradlew testDebugUnitTest` enforce these rules in CI.
 *
 * Working directory and `boxlore.projectRoot` are set to the Gradle root in
 * [core/testing/build.gradle.kts] so filesystem checks see `settings.gradle.kts`.
 */
class ArchitectureGuardTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: File(".").canonicalFile

    @Test
    fun `no feature module depends on another feature via Gradle`() {
        val featureDirs =
            File(projectRoot, "feature")
                .listFiles()
                ?.filter { it.isDirectory && File(it, "build.gradle.kts").isFile }
                .orEmpty()
        val violations = mutableListOf<String>()
        for (dir in featureDirs) {
            val buildFile = File(dir, "build.gradle.kts")
            buildFile.readLines().forEachIndexed { index, raw ->
                val line = raw.substringBefore("//").trim()
                if (line.contains("projects.feature.")) {
                    violations += "${buildFile.relativeTo(projectRoot)}:${index + 1}: $line"
                }
            }
        }
        assertTrue(
            violations.isEmpty(),
            "Feature → feature Gradle deps are forbidden:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun `no feature package imports another feature package`() {
        val featureNames =
            File(projectRoot, "feature")
                .listFiles()
                ?.filter { it.isDirectory && File(it, "build.gradle.kts").isFile }
                ?.map { it.name }
                .orEmpty()
                .toSet()
        require(featureNames.isNotEmpty()) { "No feature modules found under $projectRoot/feature" }

        // Konsist resolves directories relative to the project root (cwd); pass a
        // relative path so it is not double-prefixed.
        Konsist
            .scopeFromDirectory("feature")
            .files
            .filter { it.path.contains("/src/main/") || it.path.contains("\\src\\main\\") }
            .assertTrue { file ->
                val sourceFeature =
                    Regex("""[/\\]feature[/\\]([^/\\]+)[/\\]""")
                        .find(file.path)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?: return@assertTrue true
                val imports =
                    file.imports.map { it.name }.filter {
                        it.startsWith("cx.aswin.boxlore.feature.")
                    }
                val bad =
                    imports.filter { importName ->
                        val target =
                            importName
                                .removePrefix("cx.aswin.boxlore.feature.")
                                .substringBefore('.')
                        target in featureNames && target != sourceFeature
                    }
                bad.isEmpty()
            }
    }

    @Test
    fun `core data build file does not depend on designsystem`() {
        val buildFile = File(projectRoot, "core/data/build.gradle.kts")
        assertTrue(buildFile.isFile, "Missing ${buildFile.relativeTo(projectRoot)}")
        val activeDeps =
            buildFile.readLines().map { it.substringBefore("//").trim() }.filter { it.isNotEmpty() }
        val hits =
            activeDeps.filter {
                it.contains("designsystem", ignoreCase = true) ||
                    it.contains("projects.core.designsystem")
            }
        assertTrue(
            hits.isEmpty(),
            ":core:data must not depend on :core:designsystem:\n" + hits.joinToString("\n"),
        )
    }

    @Test
    fun `included app core and feature modules have README md`() {
        val settings = File(projectRoot, "settings.gradle.kts").readText()
        val includes =
            Regex("""include\("(:[^"]+)"\)""")
                .findAll(settings)
                .map { it.groupValues[1] }
                .filter { path ->
                    path == ":app" ||
                        path.startsWith(":core:") ||
                        path.startsWith(":feature:")
                }.toList()
        require(includes.isNotEmpty()) { "No module includes parsed from settings.gradle.kts" }

        val missing =
            includes.mapNotNull { gradlePath ->
                val folder = gradlePath.removePrefix(":").replace(':', '/')
                val readme = File(projectRoot, "$folder/README.md")
                if (readme.isFile) null else "$gradlePath → $folder/README.md"
            }
        assertTrue(
            missing.isEmpty(),
            "Every included app/core/feature module needs README.md:\n" +
                missing.joinToString("\n"),
        )
    }

    @Test
    fun `getInstance call sites stay on the allowlist`() {
        val allowedReceiver =
            Regex(
                """(?:^|[^\w.])(?:""" +
                    listOf(
                        "Calendar",
                        "java\\.util\\.Calendar",
                        "MessageDigest",
                        "WorkManager",
                        "androidx\\.work\\.WorkManager",
                        "FirebaseDatabase",
                        "FirebaseMessaging",
                        "FirebaseAppCheck",
                        "FirebaseApp",
                        "FirebaseCrashlytics",
                        "DebugAppCheckProviderFactory",
                        "PlayIntegrityAppCheckProviderFactory",
                        "com\\.google\\.firebase\\.[\\w.]+",
                    ).joinToString("|") +
                    """)\.getInstance\s*\(""",
            )
        val callPattern = Regex("""\.getInstance\s*\(""")
        val definitionPattern = Regex("""\bfun\s+getInstance\s*\(""")

        val violations = mutableListOf<String>()
        val roots =
            listOf("app", "core", "feature").map { File(projectRoot, it) }.filter { it.isDirectory }
        for (root in roots) {
            root
                .walkTopDown()
                .onEnter { dir -> dir.name != "build" && dir.name != ".gradle" }
                .filter { it.isFile && it.extension == "kt" && it.path.contains("/src/main/") }
                .forEach { file ->
                    val relative = file.relativeTo(projectRoot).path
                    val text = file.readText()
                    val declaresGetInstance = definitionPattern.containsMatchIn(text)
                    val allowFile =
                        relative.endsWith("AppContainer.kt") ||
                            relative.contains("Database.kt") ||
                            declaresGetInstance
                    text.lines().forEachIndexed { index, line ->
                        val code = line.substringBefore("//")
                        if (!callPattern.containsMatchIn(code)) return@forEachIndexed
                        if (definitionPattern.containsMatchIn(code)) return@forEachIndexed
                        if (allowFile) return@forEachIndexed
                        if (allowedReceiver.containsMatchIn(code)) return@forEachIndexed
                        violations += "$relative:${index + 1}: ${line.trim()}"
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "getInstance outside allowlist (AppContainer, companion installers, " +
                "Room DB files, WorkManager/Calendar/MessageDigest/Firebase):\n" +
                violations.joinToString("\n"),
        )
    }
}
