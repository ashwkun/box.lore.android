package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class InfoListeningProgressItemTest {
    @Test
    fun `maps entity fields used by episode playback state`() {
        val entity =
            ListeningHistoryEntity(
                episodeId = "ep-1",
                podcastId = "pod-1",
                episodeTitle = "Ep",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://example.com/a.mp3",
                podcastName = "Show",
                progressMs = 1_500L,
                durationMs = 10_000L,
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = 99L,
            )

        val item = entity.toInfoListeningProgressItem()

        assertEquals("ep-1", item.episodeId)
        assertEquals(1_500L, item.progressMs)
        assertEquals(10_000L, item.durationMs)
        assertFalse(item.isCompleted)
    }
}
