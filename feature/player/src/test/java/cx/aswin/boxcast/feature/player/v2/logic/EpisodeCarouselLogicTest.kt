package cx.aswin.boxcast.feature.player.v2.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeCarouselLogicTest {

    private val queue = listOf(
        PlayerTestFixtures.episode(id = "a"),
        PlayerTestFixtures.episode(id = "b"),
        PlayerTestFixtures.episode(id = "c"),
    )

    @Test
    fun `queueNeighbors returns previous and next for middle episode`() {
        val neighbors = EpisodeCarouselLogic.queueNeighbors(queue, "b")
        assertEquals(1, neighbors.currentIndex)
        assertEquals("a", neighbors.previous?.id)
        assertEquals("c", neighbors.next?.id)
    }

    @Test
    fun `queueNeighbors returns null previous at start of queue`() {
        val neighbors = EpisodeCarouselLogic.queueNeighbors(queue, "a")
        assertEquals(0, neighbors.currentIndex)
        assertNull(neighbors.previous)
        assertEquals("b", neighbors.next?.id)
    }

    @Test
    fun `queueNeighbors returns null next at end of queue`() {
        val neighbors = EpisodeCarouselLogic.queueNeighbors(queue, "c")
        assertEquals(2, neighbors.currentIndex)
        assertEquals("b", neighbors.previous?.id)
        assertNull(neighbors.next)
    }

    @Test
    fun `queueNeighbors returns empty neighbors when episode not in queue`() {
        val neighbors = EpisodeCarouselLogic.queueNeighbors(queue, "missing")
        assertEquals(-1, neighbors.currentIndex)
        assertNull(neighbors.previous)
        assertNull(neighbors.next)
    }

    @Test
    fun `commitThresholdPx is thirty percent of artwork width`() {
        assertEquals(90f, EpisodeCarouselLogic.commitThresholdPx(300f), 0.001f)
        assertEquals(0f, EpisodeCarouselLogic.commitThresholdPx(0f), 0.001f)
    }

    @Test
    fun `resolveSkipFromDrag commits previous on positive drag beyond threshold`() {
        assertEquals(
            CarouselSkipAction.PreviousEpisode,
            EpisodeCarouselLogic.resolveSkipFromDrag(100f, 90f),
        )
    }

    @Test
    fun `resolveSkipFromDrag commits next on negative drag beyond threshold`() {
        assertEquals(
            CarouselSkipAction.NextEpisode,
            EpisodeCarouselLogic.resolveSkipFromDrag(-100f, 90f),
        )
    }

    @Test
    fun `resolveSkipFromDrag returns none inside threshold`() {
        assertEquals(
            CarouselSkipAction.None,
            EpisodeCarouselLogic.resolveSkipFromDrag(50f, 90f),
        )
        assertEquals(
            CarouselSkipAction.None,
            EpisodeCarouselLogic.resolveSkipFromDrag(-50f, 90f),
        )
        assertEquals(
            CarouselSkipAction.None,
            EpisodeCarouselLogic.resolveSkipFromDrag(0f, 90f),
        )
    }

    @Test
    fun `resolveSkipFromDrag treats threshold boundary as none`() {
        assertEquals(
            CarouselSkipAction.None,
            EpisodeCarouselLogic.resolveSkipFromDrag(90f, 90f),
        )
        assertEquals(
            CarouselSkipAction.None,
            EpisodeCarouselLogic.resolveSkipFromDrag(-90f, 90f),
        )
    }

    @Test
    fun `resolveEpisodeImageUrl prefers episode image over podcast fallbacks`() {
        val episode = PlayerTestFixtures.episode(
            imageUrl = "https://example.com/ep.jpg",
            podcastImageUrl = "https://example.com/podcast.jpg",
            podcastId = "pod1",
        )
        val podcasts = mapOf("pod1" to PlayerTestFixtures.podcast(imageUrl = "https://example.com/map.jpg"))

        assertEquals("https://example.com/ep.jpg", EpisodeCarouselLogic.resolveEpisodeImageUrl(episode, podcasts))
    }

    @Test
    fun `resolveEpisodeImageUrl falls back to podcast image url on episode`() {
        val episode = PlayerTestFixtures.episode(
            imageUrl = "",
            podcastImageUrl = "https://example.com/podcast.jpg",
        )
        assertEquals("https://example.com/podcast.jpg", EpisodeCarouselLogic.resolveEpisodeImageUrl(episode))
    }

    @Test
    fun `resolveEpisodeImageUrl falls back to podcast map entry`() {
        val episode = PlayerTestFixtures.episode(imageUrl = null, podcastImageUrl = null, podcastId = "pod1")
        val podcasts = mapOf("pod1" to PlayerTestFixtures.podcast(imageUrl = "https://example.com/map.jpg"))
        assertEquals("https://example.com/map.jpg", EpisodeCarouselLogic.resolveEpisodeImageUrl(episode, podcasts))
    }

    @Test
    fun `resolveEpisodeImageUrl returns null when no images available`() {
        val episode = PlayerTestFixtures.episode(imageUrl = "  ", podcastImageUrl = null, podcastId = null)
        assertNull(EpisodeCarouselLogic.resolveEpisodeImageUrl(episode))
    }

    @Test
    fun `peekScaleFromDrag shrinks artwork up to four percent at threshold`() {
        assertEquals(1f, EpisodeCarouselLogic.peekScaleFromDrag(0f, 100f), 0.001f)
        assertEquals(0.96f, EpisodeCarouselLogic.peekScaleFromDrag(100f, 100f), 0.001f)
        assertEquals(0.96f, EpisodeCarouselLogic.peekScaleFromDrag(-150f, 100f), 0.001f)
        assertEquals(0.98f, EpisodeCarouselLogic.peekScaleFromDrag(50f, 100f), 0.001f)
    }
}

class UpNextLogicTest {

    private val queue = listOf(
        PlayerTestFixtures.episode(id = "now"),
        PlayerTestFixtures.episode(id = "next1"),
        PlayerTestFixtures.episode(id = "next2"),
        PlayerTestFixtures.episode(id = "next3"),
    )

    @Test
    fun `upNextEpisodes excludes current episode and preserves order`() {
        val upNext = UpNextLogic.upNextEpisodes(queue, "now", maxItems = 2)
        assertEquals(listOf("next1", "next2"), upNext.map { it.id })
    }

    @Test
    fun `upNextEpisodes returns empty when queue only contains current`() {
        val single = listOf(PlayerTestFixtures.episode(id = "solo"))
        assertEquals(emptyList<String>(), UpNextLogic.upNextEpisodes(single, "solo").map { it.id })
    }

    @Test
    fun `upNextEpisodes treats null current id as no filter`() {
        val upNext = UpNextLogic.upNextEpisodes(queue, currentEpisodeId = null, maxItems = 2)
        assertEquals(listOf("now", "next1"), upNext.map { it.id })
    }

    @Test
    fun `upNextEpisodes returns empty for zero max items`() {
        assertEquals(emptyList<String>(), UpNextLogic.upNextEpisodes(queue, "now", maxItems = 0).map { it.id })
    }

    @Test
    fun `upNextEpisodes clamps negative max items to empty list`() {
        assertEquals(emptyList<String>(), UpNextLogic.upNextEpisodes(queue, "now", maxItems = -1).map { it.id })
    }
}
