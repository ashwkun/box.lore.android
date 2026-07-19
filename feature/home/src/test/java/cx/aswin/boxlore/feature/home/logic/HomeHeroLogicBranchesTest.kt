package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeHeroLogicBranchesTest {

    private fun session(
        id: String,
        podcastId: String = "pod-$id",
        podcastTitle: String = "Show $id",
        positionMs: Long = 30_000L,
        durationMs: Long = 120_000L,
        imageUrl: String? = "https://example.com/$id.jpg",
    ): PlaybackSession = PlaybackSession(
        podcastId = podcastId,
        episodeId = "ep-$id",
        positionMs = positionMs,
        durationMs = durationMs,
        timestamp = 1L,
        episodeTitle = "Episode $id",
        podcastTitle = podcastTitle,
        imageUrl = imageUrl,
        podcastImageUrl = "https://example.com/pod-$id.jpg",
        audioUrl = "https://example.com/$id.mp3",
    )

    @Test
    fun sessionToPodcastFallsBackToParentTitleAndPodcastImage() {
        val parent = TestFixtures.podcast(id = "pod-x", title = "Parent Show")
        val podcast = HomeHeroLogic.sessionToPodcast(
            session = session(id = "x", podcastId = "pod-x", podcastTitle = "Unknown Podcast", imageUrl = null),
            subs = listOf(parent),
        )
        assertEquals("Parent Show", podcast.title)
        // No episode image -> falls back to the podcast image.
        assertEquals("https://example.com/pod-x.jpg", podcast.imageUrl)
    }

    @Test
    fun sessionToPodcastBlankPodcastIdBecomesEmptyIdAndDefaultTitle() {
        val podcast = HomeHeroLogic.sessionToPodcast(
            session = session(id = "y", podcastId = "0", podcastTitle = "").copy(durationMs = 0L),
            subs = emptyList(),
        )
        assertEquals("", podcast.id)
        assertEquals("Podcast", podcast.title)
        assertEquals(0f, podcast.resumeProgress)
    }

    @Test
    fun appendResumeItemsWithTwoSessionsAddsTwoResumeCards() {
        val hero = mutableListOf<SmartHeroItem>()
        val used = mutableSetOf<String>()
        HomeHeroLogic.appendResumeHeroItems(
            heroList = hero,
            usedPodcastIds = used,
            resumeList = listOf(session("a"), session("b")),
            subs = emptyList(),
        )
        assertEquals(2, hero.size)
        assertTrue(hero.all { it.type == HeroType.RESUME })
        assertTrue(used.containsAll(setOf("pod-a", "pod-b")))
    }

    @Test
    fun appendResumeItemsWithMoreThanTwoAddsResumeGrid() {
        val hero = mutableListOf<SmartHeroItem>()
        val used = mutableSetOf<String>()
        val sessions = (1..6).map { session("r$it") }
        HomeHeroLogic.appendResumeHeroItems(hero, used, sessions, emptyList())
        assertEquals(HeroType.RESUME, hero.first().type)
        val grid = hero.first { it.type == HeroType.RESUME_GRID }
        assertEquals("JUMP BACK IN", grid.label)
        assertNull(grid.description)
        assertTrue(grid.gridItems.orEmpty().size in 1..4)
    }

    @Test
    fun appendUnplayedItemsWithTwoAddsTwoJumpBackInCards() {
        val hero = mutableListOf<SmartHeroItem>()
        val used = mutableSetOf<String>()
        val bucket = listOf(
            TestFixtures.podcast(id = "u1").copy(latestEpisode = TestFixtures.episode(id = "e1"), preferredSort = "oldest"),
            TestFixtures.podcast(id = "u2").copy(latestEpisode = TestFixtures.episode(id = "e2")),
        )
        HomeHeroLogic.appendUnplayedHeroItems(hero, used, bucket)
        assertEquals(2, hero.size)
        assertEquals("NEXT", hero.first().label)
        assertTrue(hero.all { it.type == HeroType.JUMP_BACK_IN })
    }

    @Test
    fun appendUnplayedItemsWithMoreThanTwoAddsNewEpisodesGrid() {
        val hero = mutableListOf<SmartHeroItem>()
        val used = mutableSetOf<String>()
        val bucket = (1..5).map {
            TestFixtures.podcast(id = "u$it").copy(latestEpisode = TestFixtures.episode(id = "e$it"))
        }
        HomeHeroLogic.appendUnplayedHeroItems(hero, used, bucket)
        val grid = hero.first { it.type == HeroType.NEW_EPISODES_GRID }
        assertEquals("NEW EPISODES", grid.label)
        assertTrue(grid.gridItems.orEmpty().size in 1..4)
        assertTrue(used.contains("u1"))
    }

    @Test
    fun spotlightLabelCoversAllRegions() {
        assertEquals("#1 IN UK", HomeHeroLogic.spotlightLabel(0, "gb", "News"))
        assertEquals("#1 IN UK", HomeHeroLogic.spotlightLabel(0, "uk", ""))
        assertEquals("#1 IN FRANCE", HomeHeroLogic.spotlightLabel(0, "fr", ""))
        assertEquals("#1 IN US", HomeHeroLogic.spotlightLabel(0, "de", ""))
    }

    @Test
    fun displayPodcastForSpotlightUsesEpisodeArtWhenPresent() {
        val withEpArt = TestFixtures.podcast(id = "p1", imageUrl = "pod.jpg")
            .copy(latestEpisode = TestFixtures.episode(id = "e1", imageUrl = "ep.jpg"))
        val resultWith = HomeHeroLogic.displayPodcastForSpotlight(withEpArt)
        assertEquals("ep.jpg", resultWith.imageUrl)
        assertEquals("pod.jpg", resultWith.fallbackImageUrl)

        val withoutEpArt = TestFixtures.podcast(id = "p2", imageUrl = "pod2.jpg")
        val resultWithout = HomeHeroLogic.displayPodcastForSpotlight(withoutEpArt)
        assertEquals("pod2.jpg", resultWithout.imageUrl)
        assertEquals("pod2.jpg", resultWithout.fallbackImageUrl)
    }

    @Test
    fun appendSpotlightRespectsTargetSizeAndUsedIds() {
        val hero = mutableListOf<SmartHeroItem>()
        val used = mutableSetOf("t2")
        val trending = (1..10).map { TestFixtures.podcast(id = "t$it", title = "T$it") }
        HomeHeroLogic.appendSpotlightHeroItems(hero, used, trending, region = "us", targetSize = 3)
        assertEquals(3, hero.size)
        assertTrue(hero.none { it.podcast.id == "t2" })
        assertEquals("#1 IN US", hero.first().label)
    }
}
