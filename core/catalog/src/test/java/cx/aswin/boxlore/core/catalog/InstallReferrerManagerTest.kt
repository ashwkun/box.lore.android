package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the referrer-parsing branches of [InstallReferrerManager]. `handleReferrer` emits the
 * parsed [ReferralIntent] on [InstallReferrerManager.referralFlow]; needs Robolectric for
 * `android.net.Uri` + SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallReferrerManagerTest {
    private fun manager(): InstallReferrerManager = InstallReferrerManager(ApplicationProvider.getApplicationContext<Context>())

    private fun parse(referrer: String): ReferralIntent =
        runBlocking {
            val mgr = manager()
            withTimeout(2_000) {
                mgr.handleReferrer(referrer)
                mgr.referralFlow.first()
            }
        }

    @Test
    fun parsesQueryStyleReferrerWithTimestamp() {
        val intent = parse("type=episode&id=67890&t=150")
        assertEquals("episode", intent.type)
        assertEquals("67890", intent.id)
        assertEquals(150L, intent.timestamp)
    }

    @Test
    fun parsesQueryStyleReferrerWithPodcastIdAndRange() {
        val intent = parse("type=podcast&podcastId=12345&start=10&end=20")
        assertEquals("podcast", intent.type)
        assertEquals("12345", intent.id)
        assertEquals(10L, intent.start)
        assertEquals(20L, intent.end)
    }

    @Test
    fun parsesUnderscoreStyleReferrer() {
        val intent = parse("type_episode_id_67890_t_150")
        assertEquals("episode", intent.type)
        assertEquals("67890", intent.id)
        assertEquals(150L, intent.timestamp)
    }

    @Test
    fun parsesUnderscorePodcastReferrer() {
        val intent = parse("type_podcast_id_12345")
        assertEquals("podcast", intent.type)
        assertEquals("12345", intent.id)
    }

    @Test
    fun parsesSimpleEpisodeReferrer() {
        val intent = parse("episode_555_t_45")
        assertEquals("episode", intent.type)
        assertEquals("555", intent.id)
        assertEquals(45L, intent.timestamp)
    }

    @Test
    fun parsesSimplePodcastReferrer() {
        val intent = parse("podcast_98765")
        assertEquals("podcast", intent.type)
        assertEquals("98765", intent.id)
    }

    @Test
    fun referralIntentDefaultsAreNull() {
        val intent = ReferralIntent(type = "podcast", id = "1")
        assertNull(intent.timestamp)
        assertNull(intent.start)
        assertNull(intent.end)
    }
}
