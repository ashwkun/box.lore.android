package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoxcastPrefsTest {
    private lateinit var prefs: BoxcastPrefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = BoxcastPrefs(context)
    }

    @Test
    fun onboardingDefaultsToIncomplete() {
        assertFalse(prefs.isOnboardingCompleted())
        prefs.setOnboardingCompleted(true)
        assertTrue(prefs.isOnboardingCompleted())
    }

    @Test
    fun userGenresDefaultEmptyAndRoundTrip() {
        assertTrue(prefs.getUserGenres().isEmpty())
        prefs.setUserGenres(setOf("news", "tech"))
        assertEquals(setOf("news", "tech"), prefs.getUserGenres())
    }

    @Test
    fun recommendationsCacheDefaultsAndSave() {
        assertNull(prefs.getCachedRecommendationsJson())
        assertTrue(prefs.isRecommendationsFallback())
        prefs.saveRecommendationsCache("""[{"id":1}]""", isFallback = false)
        assertEquals("""[{"id":1}]""", prefs.getCachedRecommendationsJson())
        assertFalse(prefs.isRecommendationsFallback())
    }

    @Test
    fun bylCacheRoundTrip() {
        assertNull(prefs.getCachedBylPodcastId())
        prefs.saveBylCache(episodesJson = "[]", podcastsJson = "[]", podcastId = "42")
        assertEquals("42", prefs.getCachedBylPodcastId())
        assertEquals("[]", prefs.getCachedBylRecommendationsJson())
        assertEquals("[]", prefs.getCachedBylPodcastsJson())
    }

    @Test
    fun learnCuriosityClearRemovesKeys() {
        prefs.setDismissedCuriosityIds(setOf("1", "2"))
        prefs.setLearnCuriosityHistoryJson("""[{"episodeId":"1"}]""")
        prefs.clearLearnCuriosity()
        assertTrue(prefs.getDismissedCuriosityIds().isEmpty())
        assertNull(prefs.getLearnCuriosityHistoryJson())
    }

    @Test
    fun learnerLogEnabledUsesDefaultWhenMissing() {
        assertFalse(prefs.isLearnerLogEnabled(default = false))
        assertTrue(prefs.isLearnerLogEnabled(default = true))
        prefs.setLearnerLogEnabled(true)
        assertTrue(prefs.isLearnerLogEnabled(default = false))
    }
}
