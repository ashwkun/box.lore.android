package cx.aswin.boxlore.core.testing.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * CSV ↔ [AnalyticsGlossary] allowlist parity and emission/sdk_backed coverage inventory.
 *
 * Inventory: [docs/analytics/glossary_emission_coverage.csv]
 * Modes: `emission:<test>`, `sdk_backed:<PostHog event>`, or `person_props_only`.
 */
class GlossaryCoverageGuardTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: File(".").canonicalFile

    @Test
    fun `glossary CSV event names match AnalyticsGlossary allowlist`() {
        val csvEvents = readCsvEventNames(File(projectRoot, "docs/analytics/event_glossary.csv"))
        val allowlist =
            readAllowlistNames(
                File(
                    projectRoot,
                    "core/analytics/src/main/java/cx/aswin/boxlore/core/analytics/AnalyticsGlossary.kt",
                ),
            )
        assertEquals(
            csvEvents,
            allowlist,
            "CSV and AnalyticsGlossary allowlist diverged. " +
                "missingFromAllowlist=${csvEvents - allowlist}; " +
                "extraInAllowlist=${allowlist - csvEvents}",
        )
    }

    @Test
    fun `every glossary event has coverage inventory mode`() {
        val csvEvents = readCsvEventNames(File(projectRoot, "docs/analytics/event_glossary.csv"))
        val inventoryFile = File(projectRoot, "docs/analytics/glossary_emission_coverage.csv")
        assertTrue(inventoryFile.isFile, "Missing ${inventoryFile.relativeTo(projectRoot)}")
        val inventory = readCoverageInventory(inventoryFile)
        assertEquals(
            csvEvents,
            inventory.keys,
            "Coverage inventory must list every glossary event. " +
                "missing=${csvEvents - inventory.keys}; extra=${inventory.keys - csvEvents}",
        )
        val invalid =
            inventory.filter { (_, mode) ->
                !(
                    mode.startsWith("emission:") ||
                        mode.startsWith("sdk_backed:") ||
                        mode == "person_props_only"
                )
            }
        assertTrue(
            invalid.isEmpty(),
            "Invalid coverage_mode values (need emission:*, sdk_backed:*, or person_props_only): $invalid",
        )
        assertTrue(
            inventory.getValue("app_open").startsWith("sdk_backed:"),
            "app_open must be sdk_backed to avoid double-counting Application Opened",
        )
        assertTrue(
            inventory.getValue("app_background").startsWith("sdk_backed:"),
            "app_background must be sdk_backed to avoid double-counting Application Backgrounded",
        )
        assertEquals(
            "person_props_only",
            inventory.getValue("install_attributed"),
            "install_attributed must be person_props_only (Application Installed owns volume)",
        )
    }

    private fun readCsvEventNames(file: File): Set<String> {
        val lines = file.readLines().filter { it.isNotBlank() }
        require(lines.size > 1) { "Empty glossary CSV at $file" }
        require(lines.first().substringBefore(',').trim() == "event_name") {
            "Expected event_name as first column in $file"
        }
        return lines
            .drop(1)
            .map { it.substringBefore(',').trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun readAllowlistNames(file: File): Set<String> {
        val text = file.readText()
        val phaseAUnionB = extractSetLiteral(text, "val PHASE_A_UNION_B")
        val phaseC = extractSetLiteral(text, "val PHASE_C")
        require(phaseAUnionB.isNotEmpty() && phaseC.isNotEmpty()) {
            "Could not parse PHASE_A_UNION_B / PHASE_C from AnalyticsGlossary.kt"
        }
        return phaseAUnionB + phaseC
    }

    private fun extractSetLiteral(
        text: String,
        marker: String,
    ): Set<String> {
        val idx = text.indexOf(marker)
        if (idx < 0) return emptySet()
        val after = text.substring(idx)
        val brace = after.indexOf("setOf(")
        if (brace < 0 || brace > 200) return emptySet()
        val start = idx + brace + "setOf(".length
        var depth = 1
        var i = start
        while (i < text.length && depth > 0) {
            when (text[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        val body = text.substring(start, i - 1)
        return Regex(""""([a-z][a-z0-9_]*)"""")
            .findAll(body)
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun readCoverageInventory(file: File): Map<String, String> {
        val lines = file.readLines().filter { it.isNotBlank() }
        val header = lines.first().split(',')
        val nameIdx = header.indexOf("event_name")
        val modeIdx = header.indexOf("coverage_mode")
        require(nameIdx >= 0 && modeIdx >= 0) { "Bad coverage CSV header: ${lines.first()}" }
        return lines.drop(1).associate { line ->
            val cols = line.split(',')
            cols[nameIdx].trim() to cols[modeIdx].trim()
        }
    }
}
