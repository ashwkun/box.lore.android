package cx.aswin.boxlore.core.catalog.home

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.clearHomeCandidatesCache
import cx.aswin.boxlore.core.catalog.getHomeCandidatesV1
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.HomeSlateQualityTelemetry
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.network.model.HomeCandidateItemDto
import cx.aswin.boxlore.core.network.model.HomeCandidatesV1Response

/**
 * Android-owned Home candidate retrieval, module cache coordination, and
 * cross-module de-duplication. Final adaptive scoring of painted cards may still
 * use `:core:ranking` in the Home ViewModel.
 */
class HomePersonalizationCoordinator(
    private val podcastRepository: PodcastRepository,
) {
    data class SlateRequest(
        val modules: List<String> = emptyList(),
        val country: String,
        val languages: List<String>,
        val history: List<HistoryItem>,
        val anchorPodcastId: String? = null,
        val missionId: String? = null,
        val excludedPodcastIds: List<String> = emptyList(),
        val excludedEpisodeIds: List<String> = emptyList(),
        val subscribedPodcastIds: Set<String> = emptySet(),
        val consumedPodcastIds: Set<String> = emptySet(),
        val noveltyPreference: Double? = null,
        val daypart: String? = null,
        val revision: String? = null,
        /** When true, only refresh BYL — keep taste/mission IDs as exclusions. */
        val becauseYouLikeOnly: Boolean = false,
        val tasteLimit: Int = 12,
        val becauseYouLikeLimit: Int = 10,
        val missionLimit: Int = 8,
    )

    /**
     * Bundles the raw fetch context (cache/latency/requested modules) so [mapResponse] and
     * [HomeSlateQualityLogic.compute] don't need a long flat parameter list — see
     * [LongParameterList](https://detekt.dev/docs/rules/complexity#longparameterlist).
     */
    data class HomeCandidatesFetchMeta(
        val fromCache: Boolean,
        val cacheAgeMillis: Long?,
        val responseLatencyMillis: Long,
        val requestedModules: List<String>,
    )

    data class SlateResult(
        val taste: List<Episode>,
        val becauseYouLikeEpisodes: List<Episode>,
        val becauseYouLikePodcasts: List<Podcast>,
        val mission: List<Episode>,
        val regional: List<Episode>,
        val mode: HomePersonalizationMode,
        val isFallback: Boolean,
        val algorithmVersion: String?,
        val requestId: String?,
        val fromCache: Boolean,
        val personalizedRequestFailed: Boolean,
        /** Observational-only quality aggregate for dashboards; see [HomeSlateQualityLogic]. */
        val qualityTelemetry: HomeSlateQualityTelemetry,
    )

    suspend fun loadSlate(request: SlateRequest): SlateResult {
        val meaningfulPlays = HomeMeaningfulPlayLogic.countMeaningfulPlays(request.history)
        val hasEligibleAnchor = !request.anchorPodcastId.isNullOrBlank()
        val modeBeforeLoad =
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = meaningfulPlays,
                hasEligiblePositiveShow = hasEligibleAnchor,
                personalizedCandidatesLoaded = false,
                personalizedRequestFailed = false,
            )

        val modules =
            when {
                request.becauseYouLikeOnly -> listOf("because_you_like")
                modeBeforeLoad == HomePersonalizationMode.REGIONAL -> listOf("regional")
                request.modules.isNotEmpty() -> request.modules
                else ->
                    buildList {
                        add("taste")
                        if (hasEligibleAnchor) add("because_you_like")
                        add("mission")
                        add("regional")
                    }
            }

        val body =
            HomeCandidatesRequestBuilder.build(
                HomeCandidatesRequestBuilder.BuildRequest(
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
                ),
            )
        val fetchStartedAt = System.currentTimeMillis()
        val fetch = podcastRepository.getHomeCandidatesV1(body)
        val fetchMeta =
            HomeCandidatesFetchMeta(
                fromCache = fetch.fromCache,
                cacheAgeMillis = fetch.cacheAgeMillis,
                responseLatencyMillis = System.currentTimeMillis() - fetchStartedAt,
                requestedModules = modules,
            )
        return mapResponse(
            response = fetch.response,
            fetchMeta = fetchMeta,
            request = request,
            meaningfulPlays = meaningfulPlays,
            hasEligibleAnchor = hasEligibleAnchor,
        )
    }

    fun clearCaches() {
        clearHomeCandidatesCache()
    }

    private fun mapResponse(
        response: HomeCandidatesV1Response?,
        fetchMeta: HomeCandidatesFetchMeta,
        request: SlateRequest,
        meaningfulPlays: Int,
        hasEligibleAnchor: Boolean,
    ): SlateResult {
        val validResponse = response
        if (validResponse == null || !HomeCandidatesRequestBuilder.isValid(validResponse)) {
            return buildFallbackResult(fetchMeta, meaningfulPlays, hasEligibleAnchor)
        }
        return buildPersonalizedResult(validResponse, fetchMeta, request, meaningfulPlays, hasEligibleAnchor)
    }

    private fun buildFallbackResult(
        fetchMeta: HomeCandidatesFetchMeta,
        meaningfulPlays: Int,
        hasEligibleAnchor: Boolean,
    ): SlateResult {
        val mode =
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = meaningfulPlays,
                hasEligiblePositiveShow = hasEligibleAnchor,
                personalizedCandidatesLoaded = false,
                personalizedRequestFailed = true,
            )
        return SlateResult(
            taste = emptyList(),
            becauseYouLikeEpisodes = emptyList(),
            becauseYouLikePodcasts = emptyList(),
            mission = emptyList(),
            regional = emptyList(),
            mode = mode,
            isFallback = true,
            algorithmVersion = null,
            requestId = null,
            fromCache = fetchMeta.fromCache,
            personalizedRequestFailed = true,
            qualityTelemetry =
                HomeSlateQualityLogic.compute(
                    mode = mode,
                    algorithmVersion = null,
                    isFallback = true,
                    fetchMeta = fetchMeta,
                    rawCounts = HomeSlateQualityLogic.RawModuleCounts(0, 0, 0, 0),
                    allocated = emptyList(),
                ),
        )
    }

    private fun buildPersonalizedResult(
        validResponse: HomeCandidatesV1Response,
        fetchMeta: HomeCandidatesFetchMeta,
        request: SlateRequest,
        meaningfulPlays: Int,
        hasEligibleAnchor: Boolean,
    ): SlateResult {
        val version = validResponse.algorithmVersion

        fun mapEpisodes(items: List<HomeCandidateItemDto>): List<Episode> =
            items
                .mapNotNull { it.toEpisodeOrNull(version) }
                .distinctBy { it.id }

        val regional = mapEpisodes(validResponse.modules.regional)
        val tasteRaw = mapEpisodes(validResponse.modules.taste)
        val bylRaw = mapEpisodes(validResponse.modules.becauseYouLike)
        val missionRaw = mapEpisodes(validResponse.modules.mission)

        val allocated =
            allocateModules(
                taste = tasteRaw,
                becauseYouLike = bylRaw,
                mission = missionRaw,
                request = request,
            )

        val bylPodcasts =
            validResponse.modules.becauseYouLike
                .mapNotNull { it.toPodcastOrNull() }
                .filter { podcast ->
                    allocated.becauseYouLike.any { it.podcastId == podcast.id }
                }.distinctBy { it.id }

        val canPersonalize = modeUsesPersonalization(meaningfulPlays, hasEligibleAnchor)
        val personalizedLoaded =
            canPersonalize &&
                (
                    allocated.taste.isNotEmpty() ||
                        allocated.becauseYouLike.isNotEmpty() ||
                        allocated.mission.isNotEmpty()
                )
        val mode =
            HomePersonalizationModeLogic.derive(
                meaningfulPlayCount = meaningfulPlays,
                hasEligiblePositiveShow = hasEligibleAnchor,
                personalizedCandidatesLoaded = personalizedLoaded,
                personalizedRequestFailed = false,
            )

        // Never relabel regional charts as Taste — UI titles follow [mode].
        val tasteForUi =
            when (mode) {
                HomePersonalizationMode.PERSONALIZED -> allocated.taste
                HomePersonalizationMode.REGIONAL,
                HomePersonalizationMode.PERSONALIZING,
                -> regional.ifEmpty { allocated.taste }
            }

        return SlateResult(
            taste = tasteForUi,
            becauseYouLikeEpisodes =
                if (HomePersonalizationModeLogic.showBecauseYouLike(mode)) {
                    allocated.becauseYouLike
                } else {
                    emptyList()
                },
            becauseYouLikePodcasts =
                if (HomePersonalizationModeLogic.showBecauseYouLike(mode)) {
                    bylPodcasts
                } else {
                    emptyList()
                },
            mission = allocated.mission,
            regional = regional,
            mode = mode,
            isFallback = mode != HomePersonalizationMode.PERSONALIZED,
            algorithmVersion = version,
            requestId = validResponse.requestId,
            fromCache = fetchMeta.fromCache,
            personalizedRequestFailed = false,
            qualityTelemetry =
                HomeSlateQualityLogic.compute(
                    mode = mode,
                    algorithmVersion = version,
                    isFallback = mode != HomePersonalizationMode.PERSONALIZED,
                    fetchMeta = fetchMeta,
                    rawCounts =
                        HomeSlateQualityLogic.RawModuleCounts(
                            taste = tasteRaw.size,
                            becauseYouLike = bylRaw.size,
                            mission = missionRaw.size,
                            regional = regional.size,
                        ),
                    allocated = allocated.allocatedCandidates,
                ),
        )
    }

    private fun modeUsesPersonalization(
        meaningfulPlays: Int,
        hasEligibleAnchor: Boolean,
    ): Boolean {
        if (meaningfulPlays < HomePersonalizationModeLogic.MEANINGFUL_PLAYS_TO_ATTEMPT) {
            return false
        }
        return meaningfulPlays < HomePersonalizationModeLogic.MEANINGFUL_PLAYS_TO_REQUIRE_ANCHOR ||
            hasEligibleAnchor
    }

    private fun allocateModules(
        taste: List<Episode>,
        becauseYouLike: List<Episode>,
        mission: List<Episode>,
        request: SlateRequest,
    ): AllocatedEpisodes {
        val candidates =
            buildList {
                taste.forEachIndexed { index, episode ->
                    add(episode.toAllocCandidate(HomeSlateAllocationLogic.Module.TASTE, index, request))
                }
                becauseYouLike.forEachIndexed { index, episode ->
                    add(
                        episode.toAllocCandidate(
                            HomeSlateAllocationLogic.Module.BECAUSE_YOU_LIKE,
                            index,
                            request,
                        ),
                    )
                }
                mission.forEachIndexed { index, episode ->
                    add(episode.toAllocCandidate(HomeSlateAllocationLogic.Module.MISSION, index, request))
                }
            }
        val allocated =
            HomeSlateAllocationLogic.allocate(
                candidates = candidates,
                tasteLimit = request.tasteLimit,
                bylLimit = request.becauseYouLikeLimit,
                missionLimit = request.missionLimit,
            )
        val byId = (taste + becauseYouLike + mission).associateBy { it.id }
        return AllocatedEpisodes(
            taste = allocated.taste.mapNotNull { byId[it.episodeId] },
            becauseYouLike = allocated.becauseYouLike.mapNotNull { byId[it.episodeId] },
            mission = allocated.mission.mapNotNull { byId[it.episodeId] },
            // Candidate-level (pre-Episode-lookup) list for quality diagnostics only —
            // carries alreadyConsumedShow/isSubscription flags Episode does not.
            allocatedCandidates = allocated.taste + allocated.becauseYouLike + allocated.mission,
        )
    }

    private data class AllocatedEpisodes(
        val taste: List<Episode>,
        val becauseYouLike: List<Episode>,
        val mission: List<Episode>,
        val allocatedCandidates: List<HomeSlateAllocationLogic.Candidate>,
    )

    private fun Episode.toAllocCandidate(
        hint: HomeSlateAllocationLogic.Module,
        index: Int,
        request: SlateRequest,
    ): HomeSlateAllocationLogic.Candidate {
        val showId = podcastId.orEmpty()
        return HomeSlateAllocationLogic.Candidate(
            episodeId = id,
            podcastId = showId,
            score = (retrievalScore ?: 0.0) - (index * 0.001),
            reason = recommendationReason.orEmpty(),
            moduleHint = hint,
            isNovel = true,
            isSubscription = showId.isNotEmpty() && showId in request.subscribedPodcastIds,
            alreadyConsumedShow = showId.isNotEmpty() && showId in request.consumedPodcastIds,
        )
    }
}
