package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeListeningHistoryItemTest {
    @Test
    fun `maps entity to home history item`() {
        val entity =
            ListeningHistoryEntity(
                episodeId = "episode-1",
                podcastId = "podcast-1",
                episodeTitle = "Ignored episode title",
                episodeImageUrl = "https://example.com/episode.png",
                podcastImageUrl = null,
                episodeAudioUrl = "https://example.com/audio.mp3",
                podcastName = "Podcast One",
                progressMs = 12_345L,
                durationMs = 67_890L,
                isCompleted = true,
                isLiked = true,
                lastPlayedAt = 123_456_789L,
                isDirty = false,
                syncedAt = 111L,
                enclosureType = "audio/mpeg",
                isManualCompletion = true,
                isBulkCompletion = false,
                episodeDescription = "Ignored description",
            )

        val item = entity.toHomeListeningHistoryItem()

        assertEquals("episode-1", item.episodeId)
        assertEquals("podcast-1", item.podcastId)
        assertEquals("Podcast One", item.podcastName)
        assertNull(item.podcastImageUrl)
        assertEquals(12_345L, item.progressMs)
        assertEquals(67_890L, item.durationMs)
        assertEquals(true, item.isCompleted)
        assertEquals(true, item.isLiked)
        assertEquals(123_456_789L, item.lastPlayedAt)
    }
}
