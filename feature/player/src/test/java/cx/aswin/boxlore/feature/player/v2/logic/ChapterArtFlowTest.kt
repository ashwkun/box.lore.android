package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.feature.player.v2.chapterArtFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterArtFlowTest {
    @Test
    fun emptyChaptersEmitNullOnce() =
        runTest {
            val values =
                chapterArtFlow(
                    positionFlow = flowOf(0L, 1_000L, 10_000L),
                    chapters = emptyList(),
                ).toList()

            assertEquals(listOf<String?>(null), values)
        }

    @Test
    fun positionsMapToLatestStartedChapterArtwork() =
        runTest {
            val chapters =
                listOf(
                    chapter(10.0, "Opening", "opening.jpg"),
                    chapter(60.0, "Story", "story.jpg"),
                    chapter(120.0, "Credits", "credits.jpg"),
                )

            val values =
                chapterArtFlow(
                    positionFlow = flowOf(0L, 10_000L, 59_999L, 60_000L, 120_000L),
                    chapters = chapters,
                ).toList()

            assertEquals(
                listOf(null, "opening.jpg", "story.jpg", "credits.jpg"),
                values,
            )
        }

    @Test
    fun repeatedPositionsWithinChapterAreDistinctUntilChanged() =
        runTest {
            val chapters =
                listOf(
                    chapter(0.0, "Opening", "opening.jpg"),
                    chapter(60.0, "Story", "story.jpg"),
                )

            val values =
                chapterArtFlow(
                    positionFlow = flowOf(0L, 10_000L, 20_000L, 60_000L, 70_000L),
                    chapters = chapters,
                ).toList()

            assertEquals(listOf("opening.jpg", "story.jpg"), values)
        }

    @Test
    fun chapterWithoutArtworkEmitsNull() =
        runTest {
            val chapters =
                listOf(
                    chapter(0.0, "Opening", "opening.jpg"),
                    chapter(60.0, "Ad break", null),
                    chapter(120.0, "Story", "story.jpg"),
                )

            val values =
                chapterArtFlow(
                    positionFlow = flowOf(0L, 60_000L, 120_000L),
                    chapters = chapters,
                ).toList()

            assertEquals(listOf("opening.jpg", null, "story.jpg"), values)
        }
}
