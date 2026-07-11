package cx.aswin.boxcast.feature.player.v2.logic

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast

enum class CarouselSkipAction {
    PreviousEpisode,
    NextEpisode,
    None,
}

data class QueueEpisodeNeighbors(
    val currentIndex: Int,
    val previous: Episode?,
    val next: Episode?,
)

object EpisodeCarouselLogic {
    const val COMMIT_THRESHOLD_FRACTION = 0.3f

    fun queueNeighbors(queue: List<Episode>, currentEpisodeId: String): QueueEpisodeNeighbors {
        val currentIndex = queue.indexOfFirst { it.id == currentEpisodeId }
        if (currentIndex < 0) {
            return QueueEpisodeNeighbors(currentIndex = -1, previous = null, next = null)
        }
        val previous = queue.getOrNull(currentIndex - 1)
        val next = queue.getOrNull(currentIndex + 1)
        return QueueEpisodeNeighbors(currentIndex, previous, next)
    }

    fun commitThresholdPx(artworkWidthPx: Float): Float =
        artworkWidthPx * COMMIT_THRESHOLD_FRACTION

    fun resolveSkipFromDrag(totalDragPx: Float, commitThresholdPx: Float): CarouselSkipAction =
        when {
            totalDragPx > commitThresholdPx -> CarouselSkipAction.PreviousEpisode
            totalDragPx < -commitThresholdPx -> CarouselSkipAction.NextEpisode
            else -> CarouselSkipAction.None
        }

    fun resolveEpisodeImageUrl(
        episode: Episode,
        podcasts: Map<String, Podcast> = emptyMap(),
    ): String? {
        val podcastImage = episode.podcastId?.let { podcasts[it]?.imageUrl }
        return episode.imageUrl?.takeIf { it.isNotBlank() }
            ?: episode.podcastImageUrl?.takeIf { it.isNotBlank() }
            ?: podcastImage?.takeIf { it.isNotBlank() }
    }

    fun peekScaleFromDrag(dragOffsetPx: Float, commitThresholdPx: Float): Float {
        val dragFraction = (kotlin.math.abs(dragOffsetPx) / commitThresholdPx).coerceIn(0f, 1f)
        return 1f - dragFraction * 0.04f
    }
}

object UpNextLogic {
    fun upNextEpisodes(
        queue: List<Episode>,
        currentEpisodeId: String?,
        maxItems: Int = 2,
    ): List<Episode> = queue
        .filter { it.id != currentEpisodeId }
        .take(maxItems.coerceAtLeast(0))
}
