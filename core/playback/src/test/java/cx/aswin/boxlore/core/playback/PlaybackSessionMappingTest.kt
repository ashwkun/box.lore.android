package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackSessionMappingTest {
    @Test
    fun `fromHistoryEntity maps all session fields`() {
        val entity =
            ListeningHistoryEntity(
                episodeId = "ep-1",
                podcastId = "pod-1",
                episodeTitle = "Episode One",
                episodeImageUrl = "https://img/ep.png",
                podcastImageUrl = "https://img/pod.png",
                episodeAudioUrl = "https://audio/ep.mp3",
                podcastName = "Podcast One",
                progressMs = 12_000L,
                durationMs = 60_000L,
                isCompleted = false,
                isLiked = true,
                lastPlayedAt = 1_700_000_000_000L,
                enclosureType = "audio/mpeg",
            )

        val session = PlaybackSessionMapping.fromHistoryEntity(entity)

        assertEquals("pod-1", session.podcastId)
        assertEquals("ep-1", session.episodeId)
        assertEquals(12_000L, session.positionMs)
        assertEquals(60_000L, session.durationMs)
        assertEquals(1_700_000_000_000L, session.timestamp)
        assertEquals("Episode One", session.episodeTitle)
        assertEquals("Podcast One", session.podcastTitle)
        assertEquals("https://img/ep.png", session.imageUrl)
        assertEquals("https://img/pod.png", session.podcastImageUrl)
        assertEquals("https://audio/ep.mp3", session.audioUrl)
        assertEquals("audio/mpeg", session.enclosureType)
    }

    @Test
    fun `fromHistoryEntity preserves null optional fields`() {
        val entity =
            ListeningHistoryEntity(
                episodeId = "ep-2",
                podcastId = "pod-2",
                episodeTitle = "Bare",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = null,
                podcastName = "Pod",
                progressMs = 0L,
                durationMs = 0L,
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = 1L,
                enclosureType = null,
            )

        val session = PlaybackSessionMapping.fromHistoryEntity(entity)

        assertNull(session.imageUrl)
        assertNull(session.podcastImageUrl)
        assertNull(session.audioUrl)
        assertNull(session.enclosureType)
    }
}
