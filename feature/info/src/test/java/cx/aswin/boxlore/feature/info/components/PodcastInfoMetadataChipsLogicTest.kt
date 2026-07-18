package cx.aswin.boxlore.feature.info.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircle
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoMetadataChipsLogicTest {
    @Test
    fun `filter valid frequency episodes drops trailers bonuses and undated`() {
        val episodes =
            listOf(
                TestFixtures.episode(id = "trailer").copy(episodeType = "trailer", publishedDate = 100),
                TestFixtures.episode(id = "bonus").copy(episodeType = "bonus", publishedDate = 200),
                TestFixtures.episode(id = "undated").copy(publishedDate = 0),
                TestFixtures.episode(id = "full").copy(publishedDate = 300),
            )

        val filtered = filterValidFrequencyEpisodes(episodes)

        assertEquals(listOf("full"), filtered.map { it.id })
    }

    @Test
    fun `dormancy status returns inactive after one year`() {
        val status = dormancyStatus(daysSinceLatest = 400)

        assertEquals("Inactive / Ended", status?.first)
        assertEquals(Icons.Rounded.PauseCircle, status?.second)
    }

    @Test
    fun `dormancy status returns hiatus between six months and one year`() {
        val status = dormancyStatus(daysSinceLatest = 200)

        assertEquals("On Hiatus", status?.first)
    }

    @Test
    fun `dormancy status is null for recent releases`() {
        assertNull(dormancyStatus(daysSinceLatest = 30))
        assertNull(dormancyStatus(daysSinceLatest = null))
    }

    @Test
    fun `compute days since latest uses newest valid episode`() {
        val podcast = TestFixtures.podcast(id = "pod-1")
        val nowSeconds = System.currentTimeMillis() / 1000
        val episodes =
            listOf(
                TestFixtures.episode(id = "ep-new").copy(publishedDate = nowSeconds - 2 * 24 * 60 * 60),
                TestFixtures.episode(id = "ep-old").copy(publishedDate = nowSeconds - 10 * 24 * 60 * 60),
            )

        val days = computeDaysSinceLatest(podcast, filterValidFrequencyEpisodes(episodes))

        assertTrue(days != null && days in 1..3)
    }
}
