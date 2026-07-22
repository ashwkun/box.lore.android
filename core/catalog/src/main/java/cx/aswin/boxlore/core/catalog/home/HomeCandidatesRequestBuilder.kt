package cx.aswin.boxlore.core.catalog.home

import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.network.model.HomeCandidateItemDto
import cx.aswin.boxlore.core.network.model.HomeCandidateSeedDto
import cx.aswin.boxlore.core.network.model.HomeCandidateSeedFallbackDto
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Request
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Response
import java.util.Locale

/**
 * Builds bounded Home candidate requests and maps module pools for the
 * Android-owned slate allocator. Network I/O stays on [cx.aswin.boxlore.core.catalog.PodcastRepository].
 */
object HomeCandidatesRequestBuilder {
    /** Client-side module cache floor matching the proxy ≥4h UUID cache. */
    const val CACHE_TTL_MILLIS: Long = 4L * 60L * 60L * 1_000L

    /** Weak / early-skip history is not sent as Taste seeds. */
    private const val MIN_SEED_WEIGHT = 0.55

    /** Groups [build]'s inputs so the function stays under the [LongParameterList] threshold. */
    data class BuildRequest(
        val modules: List<String>,
        val country: String,
        val languages: List<String>,
        val history: List<HistoryItem>,
        val anchorPodcastId: String?,
        val missionId: String?,
        val excludedPodcastIds: List<String>,
        val excludedEpisodeIds: List<String>,
        val noveltyPreference: Double?,
        val daypart: String?,
        val revision: String?,
    )

    fun build(request: BuildRequest): HomeCandidatesV1Request {
        val normalizedCountry =
            request.country
                .lowercase(Locale.ROOT)
                .takeIf { it.length in 2..3 }
                ?: "us"
        return HomeCandidatesV1Request(
            requestedModules = request.modules.distinct().take(4),
            country = normalizedCountry,
            languages = request.languages,
            seeds = buildSeeds(request.history),
            anchorPodcastId = request.anchorPodcastId,
            missionId = request.missionId,
            excludedPodcastIds = request.excludedPodcastIds.distinct().take(250),
            excludedEpisodeIds = request.excludedEpisodeIds.distinct().take(250),
            noveltyPreference = request.noveltyPreference,
            daypart = request.daypart,
            revision = request.revision,
        )
    }

    fun buildSeeds(
        history: List<HistoryItem>,
        maximumSeeds: Int = 12,
    ): List<HomeCandidateSeedDto> {
        require(maximumSeeds > 0)
        return history
            .mapNotNull { item ->
                val episodeId = item.episodeId?.takeIf { it.isNotBlank() }
                val podcastId = item.podcastId?.takeIf { it.isNotBlank() }
                if (episodeId == null && podcastId == null) return@mapNotNull null
                val weight = seedWeight(item)
                if (weight < MIN_SEED_WEIGHT) return@mapNotNull null
                HomeCandidateSeedDto(
                    episodeId = episodeId,
                    podcastId = podcastId,
                    weight = weight,
                    fallback =
                        HomeCandidateSeedFallbackDto(
                            episodeTitle = item.episodeTitle.take(180),
                            podcastTitle = item.podcastTitle.take(180),
                            description = item.episodeDescription?.take(400),
                        ),
                )
            }.sortedByDescending { it.weight }
            .distinctBy { it.episodeId ?: "p:${it.podcastId}" }
            .take(maximumSeeds)
    }

    fun moduleItems(
        response: HomeCandidatesV1Response,
        module: String,
    ): List<HomeCandidateItemDto> =
        when (module) {
            "taste" -> response.modules.taste
            "because_you_like" -> response.modules.becauseYouLike
            "mission" -> response.modules.mission
            "regional" -> response.modules.regional
            else -> emptyList()
        }

    fun isValid(response: HomeCandidatesV1Response): Boolean =
        response.status == "true" &&
            response.contractVersion == 1 &&
            !response.algorithmVersion.isNullOrBlank()

    fun cacheKey(request: HomeCandidatesV1Request): String =
        buildString {
            append(request.requestedModules.sorted().joinToString(","))
            append('|')
            append(request.country)
            append('|')
            append(request.languages.sorted().joinToString(","))
            append('|')
            append(request.anchorPodcastId.orEmpty())
            append('|')
            append(request.missionId.orEmpty())
            append('|')
            append(request.revision.orEmpty())
            append('|')
            append(request.seeds.joinToString(",") { "${it.episodeId}:${it.podcastId}:${it.weight}" })
            append('|')
            append(
                request.excludedPodcastIds
                    .sorted()
                    .take(40)
                    .joinToString(","),
            )
        }

    private fun seedWeight(item: HistoryItem): Double {
        val durationMs = item.durationMs ?: 0L
        val progressRatio =
            if (durationMs > 0L) {
                (item.progressMs ?: 0L).toDouble() / durationMs
            } else {
                0.0
            }
        return when {
            item.isLiked == true -> 0.95
            item.isCompleted == true -> 0.9
            progressRatio >= 0.5 -> 0.75
            progressRatio >= 0.2 -> 0.55
            else -> 0.35
        }
    }
}
