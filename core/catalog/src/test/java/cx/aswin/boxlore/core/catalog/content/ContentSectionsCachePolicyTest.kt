package cx.aswin.boxlore.core.catalog.content

import cx.aswin.boxlore.core.network.model.ContentDurationPreferenceDto
import cx.aswin.boxlore.core.network.model.ContentSectionRecentSeedDto
import cx.aswin.boxlore.core.network.model.ContentSectionsV1Request
import cx.aswin.boxlore.core.network.model.ContentTasteSignalDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentSectionsCachePolicyTest {
    // ── Daypart resolver ───────────────────────────────────────────

    @Test
    fun daypartResolverMapsMinuteRangesToBackendPriority() {
        assertEquals("early_morning", ContentSectionsDaypartResolver.resolve(6 * 60))
        assertEquals("commute", ContentSectionsDaypartResolver.resolve(8 * 60))
        assertEquals("afternoon", ContentSectionsDaypartResolver.resolve(13 * 60))
        assertEquals("evening", ContentSectionsDaypartResolver.resolve(18 * 60))
        // Wrap-around late_night range (start > end).
        assertEquals("late_night", ContentSectionsDaypartResolver.resolve(23 * 60))
        assertEquals("late_night", ContentSectionsDaypartResolver.resolve(1 * 60))
    }

    @Test
    fun daypartResolverRejectsOutOfRangeMinute() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentSectionsDaypartResolver.resolve(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContentSectionsDaypartResolver.resolve(24 * 60)
        }
    }

    // ── Cache keys ─────────────────────────────────────────────────

    private fun fingerprint(): String =
        contentSectionsProfileFingerprint(
            ContentSectionsV1Request(
                contractVersion = 1,
                surface = "home",
                localMinuteOfDay = 600,
                country = "us",
            ),
        )

    @Test
    fun stalePrefixNormalizesCountryAndSurface() {
        val prefix =
            contentSectionsStaleCachePrefix(
                catalogVersion = 3,
                country = "US",
                surface = "HOME",
                resolvedDaypart = "morning",
                localDate = "2024-01-01",
            )
        assertTrue(prefix.startsWith("content_sections_v1e:3:2024-01-01:us:home:morning:"))
    }

    @Test
    fun stalePrefixFallsBackToUsForInvalidCountry() {
        val prefix =
            contentSectionsStaleCachePrefix(
                catalogVersion = 1,
                country = "toolong",
                surface = "explore",
                resolvedDaypart = "evening",
                localDate = "2024-01-01",
            )
        assertTrue(prefix.contains(":us:explore:"))
    }

    @Test
    fun stalePrefixRejectsUnknownSurfaceAndBadDate() {
        assertThrows(IllegalArgumentException::class.java) {
            contentSectionsStaleCachePrefix(1, "us", "unknown", "morning", "2024-01-01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            contentSectionsStaleCachePrefix(1, "us", "home", "morning", "01-01-2024")
        }
    }

    @Test
    fun cacheKeyAppendsFingerprintToStalePrefix() {
        val fp = fingerprint()
        val key =
            contentSectionsCacheKey(
                catalogVersion = 1,
                country = "us",
                surface = "home",
                resolvedDaypart = "morning",
                localDate = "2024-01-01",
                profileFingerprint = fp,
            )
        assertTrue(key.endsWith(fp))
        assertTrue(key.startsWith("content_sections_v1e:1:2024-01-01:us:home:morning:"))
    }

    @Test
    fun cacheKeyRejectsMalformedFingerprint() {
        assertThrows(IllegalArgumentException::class.java) {
            contentSectionsCacheKey(
                catalogVersion = 1,
                country = "us",
                surface = "home",
                resolvedDaypart = "morning",
                localDate = "2024-01-01",
                profileFingerprint = "NOT_HEX",
            )
        }
    }

    @Test
    fun cacheKeyByMinuteResolvesDaypart() {
        val fp = fingerprint()
        val key =
            contentSectionsCacheKey(
                catalogVersion = 1,
                country = "us",
                surface = "home",
                localMinuteOfDay = 8 * 60,
                localDate = "2024-01-01",
                profileFingerprint = fp,
            )
        assertTrue(key.contains(":commute:"))
    }

    // ── Profile fingerprint ────────────────────────────────────────

    @Test
    fun profileFingerprintIsStableAndHex() {
        val a = fingerprint()
        val b = fingerprint()
        assertEquals(a, b)
        assertTrue(a.matches(Regex("^[a-f0-9]{24}$")))
    }

    @Test
    fun profileFingerprintChangesWithInputs() {
        val base = fingerprint()
        val richer =
            contentSectionsProfileFingerprint(
                ContentSectionsV1Request(
                    contractVersion = 1,
                    surface = "home",
                    localMinuteOfDay = 600,
                    country = "us",
                    languages = listOf("en", "fr"),
                    interests = listOf(" News ", "comedy"),
                    subscribedPodcastIds = listOf(3L, 1L, 2L),
                    excludedPodcastIds = listOf(9L),
                    excludedEpisodeIds = listOf(4L),
                    recentSeeds = listOf(ContentSectionRecentSeedDto("episode", 7L, 0.5)),
                    tasteSignals = listOf(ContentTasteSignalDto("news", 0.9)),
                    recentSectionIds = listOf("s1"),
                    durationPreference = ContentDurationPreferenceDto(5, 60),
                    historyMaturity = 3,
                    noveltyPreference = 0.4,
                    localDate = "2024-01-01",
                    timezoneOffsetMinutes = -300,
                    candidateBudget = 100,
                ),
            )
        assertNotEquals(base, richer)
        assertTrue(richer.matches(Regex("^[a-f0-9]{24}$")))
    }
}
