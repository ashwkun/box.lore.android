package cx.aswin.boxlore.core.catalog.home

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Request
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Response

/**
 * Android-owned Home candidate retrieval + module cache coordination.
 * Final scoring / allocation stay in `:feature:home` and `:core:ranking`.
 */
class HomePersonalizationCoordinator(
    private val podcastRepository: PodcastRepository,
) {
    data class SlateRequest(
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
        /** When true, only refresh BYL — keep taste/mission exclusions in request. */
        val becauseYouLikeOnly: Boolean = false,
    )

    data class SlateResult(
        val taste: List<Episode>,
        val becauseYouLikeEpisodes: List<Episode>,
        val becauseYouLikePodcasts: List<Podcast>,
        val mission: List<Episode>,
        val regional: List<Episode>,
        val isFallback: Boolean,
        val algorithmVersion: String?,
        val requestId: String?,
        val fromCache: Boolean,
    )

    suspend fun loadSlate(request: SlateRequest): SlateResult {
        val modules =
            if (request.becauseYouLikeOnly) {
                listOf("because_you_like")
            } else {
                request.modules.ifEmpty {
                    listOf("taste", "because_you_like", "mission", "regional")
                }
            }
        val body =
            HomeCandidatesRequestBuilder.build(
                modules = modules,
                country = request.country,
                languages = request.languages,
                history = request.history,
                anchorPodcastId = request.anchorPodcastId,
                missionId = request.missionId,
                excludedPodcastIds = request.excludedPodcastIds,
                excludedEpisodeIds = request.excludedEpisodeIds,
                noveltyPreference = request.noveltyPreference,
                daypart = request.daypart,
                revision = request.revision,
            )
        val (response, fromCache) = podcastRepository.getHomeCandidatesV1(body)
        return mapResponse(response, fromCache)
    }

    private fun mapResponse(
        response: HomeCandidatesV1Response?,
        fromCache: Boolean,
    ): SlateResult {
        if (response == null || !HomeCandidatesRequestBuilder.isValid(response)) {
            return SlateResult(
                taste = emptyList(),
                becauseYouLikeEpisodes = emptyList(),
                becauseYouLikePodcasts = emptyList(),
                mission = emptyList(),
                regional = emptyList(),
                isFallback = true,
                algorithmVersion = null,
                requestId = null,
                fromCache = fromCache,
            )
        }
        val version = response.algorithmVersion
        fun mapEpisodes(items: List<cx.aswin.boxlore.core.network.model.HomeCandidateItemDto>) =
            items.mapNotNull { it.toEpisodeOrNull(version) }
                .distinctBy { it.id }

        val bylEpisodes = mapEpisodes(response.modules.becauseYouLike)
        val bylPodcasts =
            response.modules.becauseYouLike
                .mapNotNull { it.toPodcastOrNull() }
                .distinctBy { it.id }
        val taste = mapEpisodes(response.modules.taste)
        val regional = mapEpisodes(response.modules.regional)
        val isRegionalOnly = taste.isEmpty() && regional.isNotEmpty()
        return SlateResult(
            taste = if (isRegionalOnly) regional else taste,
            becauseYouLikeEpisodes = bylEpisodes,
            becauseYouLikePodcasts = bylPodcasts,
            mission = mapEpisodes(response.modules.mission),
            regional = regional,
            isFallback = isRegionalOnly || taste.isEmpty(),
            algorithmVersion = version,
            requestId = response.requestId,
            fromCache = fromCache,
        )
    }

    companion object {
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
                append(request.excludedPodcastIds.sorted().take(40).joinToString(","))
            }
    }
}
