package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Branch coverage for the [isLatestEpisodeNew] behavior helper. */
class PodcastLatestEpisodeNewTest {

    private val nowSeconds = System.currentTimeMillis() / 1000L

    private fun episode(id: String = "ep-1", publishedSecondsAgo: Long = 3_600L) = Episode(
        id = id,
        title = "Latest",
        description = "",
        audioUrl = "https://example.com/ep.mp3",
        publishedDate = nowSeconds - publishedSecondsAgo,
    )

    private fun podcast(
        latestEpisode: Episode? = episode(),
        episodeStatus: EpisodeStatus = EpisodeStatus.UNPLAYED,
        subscribedSecondsAgo: Long = 7_200L,
        sourceType: String = Podcast.SOURCE_PODCAST_INDEX,
        rssHasNewEpisodes: Boolean = false,
    ) = Podcast(
        id = "pod-1",
        title = "Show",
        artist = "Artist",
        imageUrl = "",
        latestEpisode = latestEpisode,
        episodeStatus = episodeStatus,
        subscribedAt = (nowSeconds - subscribedSecondsAgo) * 1000L,
        sourceType = sourceType,
        rssHasNewEpisodes = rssHasNewEpisodes,
    )

    @Test
    fun rssWithNewEpisodesFlagIsAlwaysNew() {
        val podcast = podcast(
            sourceType = Podcast.SOURCE_RSS,
            rssHasNewEpisodes = true,
            episodeStatus = EpisodeStatus.COMPLETED,
        )
        assertTrue(podcast.isLatestEpisodeNew(lastSeenId = "ep-1"))
    }

    @Test
    fun recentUnplayedEpisodeReleasedAfterSubscriptionIsNew() {
        assertTrue(podcast().isLatestEpisodeNew(lastSeenId = null))
    }

    @Test
    fun playedPodcastIsNotNew() {
        assertFalse(podcast(episodeStatus = EpisodeStatus.IN_PROGRESS).isLatestEpisodeNew(null))
    }

    @Test
    fun missingLatestEpisodeIsNotNew() {
        assertFalse(podcast(latestEpisode = null).isLatestEpisodeNew(null))
    }

    @Test
    fun unsubscribedPodcastIsNotNew() {
        val podcast = podcast().copy(subscribedAt = 0L)
        assertFalse(podcast.isLatestEpisodeNew(null))
    }

    @Test
    fun episodeOlderThanSubscriptionIsNotNew() {
        // Episode published before the subscription timestamp.
        val podcast = podcast(
            latestEpisode = episode(publishedSecondsAgo = 10_000L),
            subscribedSecondsAgo = 5_000L,
        )
        assertFalse(podcast.isLatestEpisodeNew(null))
    }

    @Test
    fun alreadySeenEpisodeIsNotNew() {
        assertFalse(podcast().isLatestEpisodeNew(lastSeenId = "ep-1"))
    }

    @Test
    fun staleEpisodeBeyond48HoursIsNotNew() {
        // Subscribed long ago; episode released just after subscription but far in the past.
        val podcast = podcast(
            latestEpisode = episode(publishedSecondsAgo = 100L * 24 * 3600),
            subscribedSecondsAgo = 101L * 24 * 3600,
        )
        assertFalse(podcast.isLatestEpisodeNew(null))
    }
}
