package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.network.model.HistoryItem
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryRecommendationSourceTest {
    @Test
    fun `fake source returns bounded history for smart downloads`() =
        runTest {
            val items =
                listOf(
                    HistoryItem(
                        podcastTitle = "A",
                        episodeTitle = "E1",
                        podcastId = "p1",
                        episodeId = "e1",
                    ),
                    HistoryItem(
                        podcastTitle = "B",
                        episodeTitle = "E2",
                        podcastId = "p2",
                        episodeId = "e2",
                    ),
                )
            val source = HistoryRecommendationSource { limit -> items.take(limit) }

            assertEquals(1, source.getHistoryForRecommendations(1).size)
            assertEquals("e1", source.getHistoryForRecommendations(1).first().episodeId)
            assertEquals(2, source.getHistoryForRecommendations(10).size)
        }
}
