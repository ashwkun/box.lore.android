package cx.aswin.boxlore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushTargetRouteAllowlistTest {
    @Test
    fun allowsKnownExactAndPrefixRoutes() {
        assertTrue(PushTargetRouteAllowlist.isAllowed("home"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("settings"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("library/downloads"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("podcast/123"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("episode/abc?autoplay=true"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("boxlore://episode/abc"))
        assertTrue(PushTargetRouteAllowlist.isAllowed("https://aswin.cx/boxlore/share?type=episode&id=1"))
    }

    @Test
    fun rejectsUnknownAndEmpty() {
        assertFalse(PushTargetRouteAllowlist.isAllowed(""))
        assertFalse(PushTargetRouteAllowlist.isAllowed("javascript:alert(1)"))
        assertFalse(PushTargetRouteAllowlist.isAllowed("file:///etc/passwd"))
        assertFalse(PushTargetRouteAllowlist.isAllowed("evil/path"))
        assertNull(PushTargetRouteAllowlist.sanitize(null))
        assertNull(PushTargetRouteAllowlist.sanitize("not-a-route"))
    }

    @Test
    fun sanitizeReturnsTrimmedAllowed() {
        assertEquals("home", PushTargetRouteAllowlist.sanitize("  home  "))
        assertEquals(
            "boxlore://podcast/99",
            PushTargetRouteAllowlist.sanitize("boxlore://podcast/99"),
        )
    }
}
