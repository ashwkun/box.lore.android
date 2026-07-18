package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.domain.ports.OfflineEpisodeSnapshot

/**
 * Pure merge of nav/deep-link episode fields with offline download/history snapshots.
 * Extracted from [cx.aswin.boxlore.feature.info.EpisodeInfoViewModel] for JVM tests.
 */
object EpisodeOfflineMergeLogic {
    data class EpisodeFieldSeed(
        val podcastId: String,
        val podcastTitle: String,
        val episodeTitle: String,
        val episodeImageUrl: String,
        val episodeDescription: String,
        val episodeAudioUrl: String,
        val episodeDurationSec: Int,
    )

    fun merge(
        seed: EpisodeFieldSeed,
        snapshot: OfflineEpisodeSnapshot?,
    ): EpisodeFieldSeed {
        if (snapshot == null) return seed
        val genericTitle = seed.podcastTitle.isEmpty() || seed.podcastTitle == "Podcast"
        return seed.copy(
            podcastId = seed.podcastId.ifEmpty { snapshot.podcastId },
            podcastTitle = if (genericTitle) snapshot.podcastName else seed.podcastTitle,
            episodeTitle = seed.episodeTitle.ifEmpty { snapshot.episodeTitle },
            episodeImageUrl = seed.episodeImageUrl.ifEmpty { snapshot.episodeImageUrl.orEmpty() },
            episodeDescription =
                seed.episodeDescription.ifEmpty {
                    snapshot.episodeDescription.orEmpty()
                },
            episodeAudioUrl = seed.episodeAudioUrl.ifEmpty { snapshot.audioUrl },
            episodeDurationSec =
                if (seed.episodeDurationSec == 0) {
                    (snapshot.durationMs / 1000L).toInt()
                } else {
                    seed.episodeDurationSec
                },
        )
    }
}
