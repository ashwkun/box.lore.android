package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeHeroLogicTest {
    @Test
    fun `session to podcast uses episode art and resume ratio`() {
        val session =
            PlaybackSession(
                podcastId = "pod-1",
                episodeId = "ep-1",
                positionMs = 60_000L,
                durationMs = 120_000L,
                timestamp = 1L,
                episodeTitle = "Ep",
                podcastTitle = "Show",
                imageUrl = "https://example.com/ep.jpg",
                podcastImageUrl = "https://example.com/pod.jpg",
                audioUrl = "https://example.com/a.mp3",
            )

        val podcast = HomeHeroLogic.sessionToPodcast(session, subs = emptyList())

        assertEquals("pod-1", podcast.id)
        assertEquals("Show", podcast.title)
        assertEquals("https://example.com/ep.jpg", podcast.imageUrl)
        assertEquals(0.5f, podcast.resumeProgress)
        assertEquals("ep-1", podcast.latestEpisode?.id)
    }

    @Test
    fun `unplayed hero label prefers NEXT for oldest sort`() {
        assertEquals("NEXT", HomeHeroLogic.unplayedHeroLabel("oldest", unplayedCount = 3, isFirst = true))
        assertEquals("NEW EPISODE", HomeHeroLogic.unplayedHeroLabel("newest", unplayedCount = 1, isFirst = true))
        assertEquals("FRESH DROP", HomeHeroLogic.unplayedHeroLabel("newest", unplayedCount = 3, isFirst = true))
    }

    @Test
    fun `build hero items adds resume then unplayed then spotlight`() {
        val sub = TestFixtures.podcast(id = "sub-1", title = "Sub")
        val resume =
            listOf(
                PlaybackSession(
                    podcastId = "sub-1",
                    episodeId = "ep-r",
                    positionMs = 30_000L,
                    durationMs = 120_000L,
                    timestamp = 1L,
                    episodeTitle = "Resume Ep",
                    podcastTitle = "Sub",
                    imageUrl = null,
                    podcastImageUrl = "https://example.com/pod.jpg",
                    audioUrl = null,
                ),
            )
        val unplayed = listOf(sub.copy(latestEpisode = TestFixtures.episode(id = "ep-new")))
        val trending =
            listOf(
                TestFixtures.podcast(id = "trend-1", title = "Trend"),
                TestFixtures.podcast(id = "trend-2", title = "Trend2"),
            )

        val hero =
            HomeHeroLogic.buildHeroItems(
                resumeList = resume,
                unplayedBucket = unplayed,
                trendingList = trending,
                subs = listOf(sub),
                region = "us",
            )

        assertEquals(HeroType.RESUME, hero.first().type)
        assertTrue(hero.any { it.type == HeroType.JUMP_BACK_IN })
        assertTrue(hero.any { it.type == HeroType.SPOTLIGHT })
        assertEquals("#1 IN US", hero.first { it.type == HeroType.SPOTLIGHT }.label)
    }

    @Test
    fun `spotlight label uses region and genre`() {
        assertEquals("#1 IN INDIA", HomeHeroLogic.spotlightLabel(0, "in", ""))
        assertEquals("TRENDING IN NEWS", HomeHeroLogic.spotlightLabel(1, "us", "News"))
        assertEquals("TRENDING", HomeHeroLogic.spotlightLabel(1, "us", "Podcast"))
    }
}
