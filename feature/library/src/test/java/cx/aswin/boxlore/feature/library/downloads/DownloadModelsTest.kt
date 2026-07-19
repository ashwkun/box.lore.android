package cx.aswin.boxlore.feature.library.downloads

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadModelsTest {

    private fun entity(
        episodeId: String = "ep-1",
        podcastId: String = "pod-1",
        durationMs: Long = 120_000L,
    ): DownloadedEpisodeEntity = DownloadedEpisodeEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Episode Title",
        episodeDescription = "Episode description",
        episodeImageUrl = "https://example.com/ep.jpg",
        podcastName = "Podcast Name",
        podcastImageUrl = "https://example.com/pod.jpg",
        durationMs = durationMs,
        publishedDate = 42L,
        localFilePath = "/downloads/ep-1.mp3",
        downloadId = 7L,
        downloadedAt = 1_000L,
        sizeBytes = 2_048L,
    )

    @Test
    fun toEpisodeMapsLocalFileAsAudioAndDurationInSeconds() {
        val episode = entity(durationMs = 180_000L).toEpisode()
        assertEquals("ep-1", episode.id)
        assertEquals("Episode Title", episode.title)
        assertEquals("/downloads/ep-1.mp3", episode.audioUrl)
        assertEquals(180, episode.duration)
        assertEquals(42L, episode.publishedDate)
        assertEquals("https://example.com/pod.jpg", episode.podcastImageUrl)
    }

    @Test
    fun toEpisodeDefaultsMissingDescriptionToEmpty() {
        val episode = entity().copy(episodeDescription = null).toEpisode()
        assertEquals("", episode.description)
    }

    @Test
    fun toPodcastMapsIdentityFields() {
        val podcast = entity().toPodcast()
        assertEquals("pod-1", podcast.id)
        assertEquals("Podcast Name", podcast.title)
        assertEquals("https://example.com/pod.jpg", podcast.imageUrl)
        assertEquals("", podcast.artist)
    }

    @Test
    fun toPodcastDefaultsNullImageToEmpty() {
        val podcast = entity().copy(podcastImageUrl = null).toPodcast()
        assertEquals("", podcast.imageUrl)
    }

    @Test
    fun formatSizeCoversAllUnitBuckets() {
        assertEquals("0 B", formatSize(0L))
        assertEquals("0 B", formatSize(-5L))
        assertEquals("512.0 B", formatSize(512L))
        assertEquals("1.0 KB", formatSize(1024L))
        assertEquals("1.0 MB", formatSize(1024L * 1024L))
        assertEquals("1.0 GB", formatSize(1024L * 1024L * 1024L))
    }

    @Test
    fun formatRelativeDateReturnsEmptyForZero() {
        assertEquals("", formatRelativeDate(0L))
    }

    @Test
    fun formatRelativeDateBucketsElapsedTime() {
        val nowSeconds = System.currentTimeMillis() / 1000
        assertEquals("Just now", formatRelativeDate(nowSeconds + 100))
        assertTrue(formatRelativeDate(nowSeconds - 120).endsWith("m ago"))
        assertTrue(formatRelativeDate(nowSeconds - 7_200).endsWith("h ago"))
        assertTrue(formatRelativeDate(nowSeconds - 172_800).endsWith("d ago"))
        assertTrue(formatRelativeDate(nowSeconds - 1_209_600).endsWith("w ago"))
        assertTrue(formatRelativeDate(nowSeconds - 5_184_000).endsWith("mo ago"))
        assertTrue(formatRelativeDate(nowSeconds - 63_072_000).endsWith("y ago"))
    }
}
