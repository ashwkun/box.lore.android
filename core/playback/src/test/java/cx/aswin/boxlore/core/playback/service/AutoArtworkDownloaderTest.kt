package cx.aswin.boxlore.core.playback.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoArtworkDownloaderTest {
    @Test
    fun parseHttpsUrlUpgradesHttpAndRejectsJunk() {
        val https = AutoArtworkDownloader.parseHttpsUrl("https://cdn.example/art.jpg")!!
        assertEquals("https", https.protocol)
        assertEquals("cdn.example", https.host)

        val upgraded = AutoArtworkDownloader.parseHttpsUrl("http://cdn.example/art.jpg")!!
        assertEquals("https", upgraded.protocol)
        assertEquals("cdn.example", upgraded.host)

        assertNull(AutoArtworkDownloader.parseHttpsUrl("not a url"))
        assertNull(AutoArtworkDownloader.parseHttpsUrl("ftp://cdn.example/x.jpg"))
    }

    @Test
    fun isPublicHttpsUrlRejectsLoopbackAndNonHttps() {
        val loopback = URI("https://127.0.0.1/cover.jpg").toURL()
        assertFalse(AutoArtworkDownloader.isPublicHttpsUrl(loopback))

        val http = URI("http://example.com/cover.jpg").toURL()
        assertFalse(AutoArtworkDownloader.isPublicHttpsUrl(http))

        val weirdPort = URI("https://example.com:8443/cover.jpg").toURL()
        assertFalse(AutoArtworkDownloader.isPublicHttpsUrl(weirdPort))
    }

    @Test
    fun isPublicHttpsUrlAcceptsPublicHttpsHost() {
        // example.com resolves to documentation IPs that are not site-local.
        val url = URI("https://example.com/cover.jpg").toURL()
        assertTrue(AutoArtworkDownloader.isPublicHttpsUrl(url))
    }
}
