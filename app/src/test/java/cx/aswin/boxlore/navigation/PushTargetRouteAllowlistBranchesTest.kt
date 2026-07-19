package cx.aswin.boxlore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Supplementary branch coverage for [PushTargetRouteAllowlist] — [isAppOrWebUri] scheme
 * detection and the exact/prefix route tables not exercised by the primary test.
 */
class PushTargetRouteAllowlistBranchesTest {
    @Test
    fun isAppOrWebUriRecognizesAllSchemes() {
        assertTrue(PushTargetRouteAllowlist.isAppOrWebUri("http://example.com"))
        assertTrue(PushTargetRouteAllowlist.isAppOrWebUri("https://example.com"))
        assertTrue(PushTargetRouteAllowlist.isAppOrWebUri("boxlore://podcast/1"))
        assertTrue(PushTargetRouteAllowlist.isAppOrWebUri("boxcast://episode/1"))
    }

    @Test
    fun isAppOrWebUriRejectsRelativeAndUnknownSchemes() {
        assertFalse(PushTargetRouteAllowlist.isAppOrWebUri("home"))
        assertFalse(PushTargetRouteAllowlist.isAppOrWebUri("podcast/1"))
        assertFalse(PushTargetRouteAllowlist.isAppOrWebUri("javascript:alert(1)"))
    }

    @Test
    fun allowsEveryExactRoute() {
        listOf(
            "home",
            "explore",
            "library",
            "settings",
            "debug",
            "onboarding",
            "library/liked",
            "library/history",
            "library/downloads",
            "library/subscriptions",
            "library/downloads/settings",
            "library/auto_downloads/settings",
        ).forEach { route ->
            assertTrue("expected $route allowed", PushTargetRouteAllowlist.isAllowed(route))
        }
    }

    @Test
    fun allowsEveryPrefixRoute() {
        assertTrue(PushTargetRouteAllowlist.isAllowed("podcast/42"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("episode/xyz"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("settings?section=playback"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("explore?query=tech"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("briefing"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("briefing/today"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("library/anything-else"))
    }

    @Test
    fun trimsWhitespaceBeforeMatching() {
        assertTrue(PushTargetRouteAllowlist.isAllowed("   home   "))
        assertFalse(PushTargetRouteAllowlist.isAllowed("   "))
    }

    @Test
    fun sanitizeReturnsNullForBlankAndDisallowed() {
        assertNull(PushTargetRouteAllowlist.sanitize(""))
        assertNull(PushTargetRouteAllowlist.sanitize("   "))
        assertNull(PushTargetRouteAllowlist.sanitize("totally/unknown/deep/path"))
    }

    @Test
    fun sanitizePreservesAppSchemeUnchanged() {
        assertEquals("boxcast://home", PushTargetRouteAllowlist.sanitize("boxcast://home"))
    }
}
