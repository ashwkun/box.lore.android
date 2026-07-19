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
    fun `core catalog build file does not depend on designsystem`() {
        val buildFile = File(projectRoot, "core/catalog/build.gradle.kts")
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
            ":core:catalog must not depend on :core:designsystem:\n" + hits.joinToString("\n"),
        )
    }

    @Test
    fun `core catalog build file does not depend on playback`() {
        val buildFile = File(projectRoot, "core/catalog/build.gradle.kts")
        assertTrue(buildFile.isFile, "Missing ${buildFile.relativeTo(projectRoot)}")
        val activeDeps =
            buildFile.readLines().map { it.substringBefore("//").trim() }.filter { it.isNotEmpty() }
        val hits =
            activeDeps.filter {
                it.contains("projects.core.playback") ||
                    Regex("""\b:core:playback\b""").containsMatchIn(it)
            }
        assertTrue(
            hits.isEmpty(),
            ":core:catalog must not depend on :core:playback:\n" + hits.joinToString("\n"),
        )
    }

    @Test
    fun `core catalog does not api-export analytics or ranking`() {
        val buildFile = File(projectRoot, "core/catalog/build.gradle.kts")
        assertTrue(buildFile.isFile, "Missing ${buildFile.relativeTo(projectRoot)}")
        val violations = mutableListOf<String>()
        buildFile.readLines().forEachIndexed { index, raw ->
            val line = raw.substringBefore("//").trim()
            if (!line.startsWith("api(")) return@forEachIndexed
            if (line.contains("projects.core.analytics") || line.contains("projects.core.ranking")) {
                violations += "${buildFile.relativeTo(projectRoot)}:${index + 1}: $line"
            }
        }
        assertTrue(
            violations.isEmpty(),
            ":core:catalog must not api( analytics or ranking ); use implementation or none:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `no Hilt Koin Dagger or MockK in production sources or Gradle deps`() {
        val forbiddenGradle =
            Regex(
                """(?i)(hilt|koin|dagger|mockk|com\.google\.dagger|io\.insert-koin|io\.mockk)""",
            )
        val forbiddenImport =
            Regex(
                """^import\s+(dagger\.|.*\.hilt\.|org\.koin\.|io\.mockk\.)""",
            )
        val violations = mutableListOf<String>()

        val buildFiles =
            listOf("app", "core", "feature")
                .map { File(projectRoot, it) }
                .filter { it.isDirectory }
                .flatMap { root ->
                    root
                        .walkTopDown()
                        .onEnter { dir -> dir.name != "build" && dir.name != ".gradle" }
                        .filter { it.isFile && it.name == "build.gradle.kts" }
                        .toList()
                }
        for (buildFile in buildFiles) {
            buildFile.readLines().forEachIndexed { index, raw ->
                val line = raw.substringBefore("//").trim()
                if (line.isEmpty()) return@forEachIndexed
                // Comments about "no MockK" are fine; only dependency-like lines.
                if (!line.contains("implementation") &&
                    !line.contains("api(") &&
                    !line.contains("testImplementation") &&
                    !line.contains("androidTestImplementation") &&
                    !line.contains("ksp(") &&
                    !line.contains("classpath") &&
                    !line.contains("alias(libs")
                ) {
                    return@forEachIndexed
                }
                if (forbiddenGradle.containsMatchIn(line) &&
                    !line.contains("no MockK", ignoreCase = true)
                ) {
                    violations +=
                        "${buildFile.relativeTo(projectRoot)}:${index + 1}: $line"
                }
            }
        }

        val sourceRoots =
            listOf("app", "core", "feature").map { File(projectRoot, it) }.filter { it.isDirectory }
        for (root in sourceRoots) {
            root
                .walkTopDown()
                .onEnter { dir -> dir.name != "build" && dir.name != ".gradle" }
                .filter {
                    it.isFile &&
                        it.extension == "kt" &&
                        (
                            it.path.contains("/src/main/") ||
                                it.path.contains("/src/test/") ||
                                it.path.contains("/src/androidTest/")
                        )
                }.forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        val code = line.trim()
                        if (forbiddenImport.containsMatchIn(code)) {
                            violations +=
                                "${file.relativeTo(projectRoot)}:${index + 1}: $code"
                        }
                    }
                }
        }

        assertTrue(
            violations.isEmpty(),
            "Hilt/Koin/Dagger/MockK are forbidden (manual AppContainer + fakes):\n" +
                violations.joinToString("\n"),
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

    /**
     * AndroidViewModels that are hard to construct hermetically (Application-backed, heavy repo
     * graphs) but whose behaviour is already exercised by hermetic `logic/` package suites.
     * These are allowlisted so the guard still forces a matching `*Test.kt` for any NEW ViewModel.
     */
    private val viewModelTestAllowlist =
        setOf(
            "BriefingViewModel",
            "DebugViewModel",
            "EpisodeInfoViewModel",
            "ExploreViewModel",
            "HistoryViewModel",
            "LearnHistoryViewModel",
            "LearnViewModel",
            "LibraryViewModel",
            "OnboardingViewModel",
        )

    /**
     * Repositories covered by differently-named suites or irreducible Media3 orchestration.
     * `PlaybackRepository` is excluded from the merged line gate entirely (see docs/TESTING.md).
     */
    private val repositoryTestAllowlist =
        setOf(
            // Media3 MediaController orchestration; covered by policy unit tests + Maestro.
            "PlaybackRepository",
            // Media3 DownloadManager-bound; covered by AutoDownload/SmartDownload worker suites.
            "DownloadRepository",
            // Covered by RssRepositoryHelpers/RssSourceMatcher suites + Room DAO paths.
            "RssPodcastRepository",
        )

    @Test
    fun `new ViewModels and Repositories ship with a matching test suite`() {
        val stubMarker = "/src/main/java/cx/aswin/boxlore/core/data/"
        val roots =
            listOf("app", "core", "feature").map { File(projectRoot, it) }.filter { it.isDirectory }

        val mainFiles =
            roots.flatMap { root ->
                root
                    .walkTopDown()
                    .onEnter { dir -> dir.name != "build" && dir.name != ".gradle" }
                    .filter { file ->
                        file.isFile &&
                            file.extension == "kt" &&
                            file.path.replace('\\', '/').contains("/src/main/") &&
                            (
                                file.name.endsWith("ViewModel.kt") ||
                                    file.name.endsWith("Repository.kt")
                            )
                    }.toList()
            }
        require(mainFiles.isNotEmpty()) { "No ViewModel/Repository sources found under $projectRoot" }

        val violations = mutableListOf<String>()
        for (mainFile in mainFiles) {
            val normalized = mainFile.path.replace('\\', '/')
            if (normalized.contains(stubMarker)) continue

            val baseName = mainFile.nameWithoutExtension
            val isViewModel = baseName.endsWith("ViewModel")
            val allowlist = if (isViewModel) viewModelTestAllowlist else repositoryTestAllowlist
            if (baseName in allowlist) continue

            val moduleRoot = normalized.substringBefore("/src/main/")
            val testDirs =
                listOf("$moduleRoot/src/test", "$moduleRoot/src/androidTest").map(::File)
            val hasMatchingTest =
                testDirs
                    .filter { it.isDirectory }
                    .any { dir ->
                        dir
                            .walkTopDown()
                            .any { it.isFile && it.name.startsWith(baseName) && it.name.endsWith("Test.kt") }
                    }
            if (!hasMatchingTest) {
                violations +=
                    "${mainFile.relativeTo(projectRoot).path.replace('\\', '/')}: " +
                    "needs a matching $baseName*Test.kt under the module's src/test or " +
                    "src/androidTest (or add it to the documented allowlist in ArchitectureGuardTest)"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "New *ViewModel / *Repository sources must ship with a similarly named test suite:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `extracted core modules keep package equal to module with stub allowlist`() {
        // Permanent upgrade stubs may live under core.data (workers / services).
        val stubAllowlistPrefixes =
            listOf(
                "core/downloads/src/main/java/cx/aswin/boxlore/core/data/",
                "core/playback/src/main/java/cx/aswin/boxlore/core/data/",
            )
        val modulePackageRoots =
            mapOf(
                "catalog" to "cx.aswin.boxlore.core.catalog",
                "playback" to "cx.aswin.boxlore.core.playback",
                "downloads" to "cx.aswin.boxlore.core.downloads",
                "prefs" to "cx.aswin.boxlore.core.prefs",
                "analytics" to "cx.aswin.boxlore.core.analytics",
                "rss" to "cx.aswin.boxlore.core.rss",
                "ranking" to "cx.aswin.boxlore.core.ranking",
                "database" to "cx.aswin.boxlore.core.database",
            )
        val violations =
            modulePackageRoots.flatMap { (module, expectedRoot) ->
                packageModuleViolationsFor(
                    module = module,
                    expectedRoot = expectedRoot,
                    stubAllowlistPrefixes = stubAllowlistPrefixes,
                )
            }
        assertTrue(
            violations.isEmpty(),
            "package=module violated (stub paths under core/data are allowlisted):\n" +
                violations.joinToString("\n"),
        )
    }

    private fun packageModuleViolationsFor(
        module: String,
        expectedRoot: String,
        stubAllowlistPrefixes: List<String>,
    ): List<String> {
        val mainJava = File(projectRoot, "core/$module/src/main/java")
        if (!mainJava.isDirectory) {
            return listOf("missing sources: core/$module/src/main/java")
        }
        return mainJava
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                packageDeclarationViolation(
                    file = file,
                    expectedRoot = expectedRoot,
                    stubAllowlistPrefixes = stubAllowlistPrefixes,
                )
            }.toList()
    }

    private fun packageDeclarationViolation(
        file: File,
        expectedRoot: String,
        stubAllowlistPrefixes: List<String>,
    ): String? {
        val relative = file.relativeTo(projectRoot).path.replace('\\', '/')
        val pkg =
            Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
                .find(file.readText())
                ?.groupValues
                ?.getOrNull(1)
        if (stubAllowlistPrefixes.any { relative.startsWith(it) }) {
            return if (pkg == null || !pkg.startsWith("cx.aswin.boxlore.core.data")) {
                "$relative: stub allowlist requires package under " +
                    "cx.aswin.boxlore.core.data (found $pkg)"
            } else {
                null
            }
        }
        if (pkg == null) return "$relative: missing package declaration"
        if (pkg != expectedRoot && !pkg.startsWith("$expectedRoot.")) {
            return "$relative: package $pkg must be $expectedRoot or a subpackage"
        }
        return null
    }
}
