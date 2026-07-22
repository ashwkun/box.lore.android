package cx.aswin.boxlore.feature.home

import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.catalog.home.HomeAnchorSelectionLogic
import cx.aswin.boxlore.core.catalog.home.HomeDiscoveryMission
import cx.aswin.boxlore.core.catalog.home.HomeDiscoveryMissionLogic
import cx.aswin.boxlore.core.catalog.home.HomePersonalizationCoordinator
import cx.aswin.boxlore.core.catalog.home.recommendationLanguagesForCountry
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.getHistoryForRecommendations
import cx.aswin.boxlore.core.ranking.CandidateFeatureBuilder
import cx.aswin.boxlore.core.ranking.CandidateSignals
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingExposure
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.home.components.RecommendationFeedbackAction
import cx.aswin.boxlore.feature.home.logic.HomeAnchorConfidenceLogic
import cx.aswin.boxlore.feature.home.logic.HomeFeedbackLogic
import cx.aswin.boxlore.feature.home.logic.HomeMissionContextLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Home rebuild: Taste / Because-You-Like / greeting mission slate loading through
 * [HomePersonalizationCoordinator.loadSlate]. Replaces the retired per-rail heuristics that used
 * to live in `HomeViewModelBecauseYouLike` / the old personalized-recommendations fetch.
 *
 * BYL anchor resolution combines [HomeAnchorSelectionLogic] with the adaptive learner's
 * `topShowAffinities` / `facetConfidence` and a DataStore manual override. Long-press feedback
 * (More like this / Not for me / Hide this show) is wired to
 * [cx.aswin.boxlore.core.ranking.RankingFeedbackRepository] via [onRecommendationFeedback].
 */
private data class HomeSlateTrigger(
    val region: String,
    val daypart: ContentDaypart,
    val subscribedPodcasts: List<Podcast>,
    val manualAnchorOverride: String?,
    val locallyHiddenPodcastIds: Set<String>,
    val revision: Int,
)

internal fun HomeViewModel.observeHomePersonalization() {
    viewModelScope.launch {
        combine(
            combine(
                userPrefs.regionStream,
                clockContextFlow.map { it.daypart }.distinctUntilChanged(),
                subscriptionRepository.subscribedPodcasts,
                userPrefs.overriddenRecPodcastIdStream,
                _locallyHiddenPodcastIds,
            ) { region, daypart, subs, manualOverride, hiddenIds ->
                HomeSlateTriggerCore(region, daypart, subs, manualOverride, hiddenIds)
            },
            _slateRevision,
        ) { core, revision ->
            HomeSlateTrigger(
                region = core.region,
                daypart = core.daypart,
                subscribedPodcasts = core.subscribedPodcasts,
                manualAnchorOverride = core.manualAnchorOverride,
                locallyHiddenPodcastIds = core.locallyHiddenPodcastIds,
                revision = revision,
            )
        }.distinctUntilChanged()
            .collectLatest { trigger -> loadHomeSlate(trigger) }
    }
}

private data class HomeSlateTriggerCore(
    val region: String,
    val daypart: ContentDaypart,
    val subscribedPodcasts: List<Podcast>,
    val manualAnchorOverride: String?,
    val locallyHiddenPodcastIds: Set<String>,
)

/**
 * One-shot gate: drops stale ranking / cached slate state so the rebuilt path starts clean for
 * upgrading users. Bumps [_slateRevision] afterwards so [observeHomePersonalization] — already
 * running from `init` and possibly mid-flight against pre-reset state — reallocates against the
 * now-clean state rather than silently keeping a stale first load.
 */
internal suspend fun HomeViewModel.runFirstLaunchPersonalizationResetIfNeeded() {
    if (userPrefs.hasCompletedFirstLaunchPersonalizationReset()) return
    try {
        rankingFeedback.reset()
        homePersonalizationCoordinator.clearCaches()
        boxcastPrefs.clearRecommendationCaches()
        userPrefs.setOverriddenRecPodcastId(null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("HomeViewModel", "First-launch personalization reset failed", e)
    } finally {
        userPrefs.markFirstLaunchPersonalizationResetDone()
        bumpSlateRevision()
    }
}

private suspend fun HomeViewModel.loadHomeSlate(trigger: HomeSlateTrigger) {
    _isBecauseYouLikeLoading.value = true
    try {
        val hardExcluded = adaptiveRankingRepository.hardExcludedPodcastIds()
        val anchorPodcastId = resolveAnchorPodcastId(trigger, hardExcluded)
        val anchorPodcast = anchorPodcastId?.let { resolveAnchorPodcast(it, trigger.subscribedPodcasts) }
        _seemsToLikePodcast.value = anchorPodcast

        val mission = resolveDiscoveryMission(trigger.daypart)

        val history = playbackRepository.getHistoryForRecommendations(HOME_SLATE_HISTORY_LIMIT)
        val excludedPodcastIds = (hardExcluded + trigger.locallyHiddenPodcastIds).toList()

        val request =
            HomePersonalizationCoordinator.SlateRequest(
                country = trigger.region,
                languages = recommendationLanguagesForCountry(trigger.region),
                history = history,
                anchorPodcastId = anchorPodcast?.id,
                missionId = mission.id,
                excludedPodcastIds = excludedPodcastIds,
                subscribedPodcastIds = trigger.subscribedPodcasts.map { it.id }.toSet(),
                daypart = HomeMissionContextLogic.daypartId(trigger.daypart),
            )

        val result = homePersonalizationCoordinator.loadSlate(request)
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .trackHomeSlateQualitySnapshot(result.qualityTelemetry)
        val missionEpisodes = result.mission

        _personalizationMode.value = result.mode
        _recommendations.value = result.taste
        _becauseYouLikeRecommendations.value = result.becauseYouLikeEpisodes
        _becauseYouLikePodcasts.value = result.becauseYouLikePodcasts
        _missionEpisodes.value = missionEpisodes
        // Omit empty missions rather than showing an empty rail with a title.
        _activeMission.value = mission.takeIf { missionEpisodes.isNotEmpty() }
        _isRecommendationsFallback.value = result.isFallback
        _isRecommendationsLoaded.value = true

        recordSlateExposures(result.taste, result.becauseYouLikeEpisodes, missionEpisodes)
        cacheSlateResult(result.taste, result.becauseYouLikeEpisodes, result.becauseYouLikePodcasts, anchorPodcast?.id, result.isFallback)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.e("HomeViewModel", "Home slate load failed", e)
        _isRecommendationsLoaded.value = true
    } finally {
        _isBecauseYouLikeLoading.value = false
    }
}

private suspend fun HomeViewModel.resolveAnchorPodcastId(
    trigger: HomeSlateTrigger,
    hardExcluded: Set<String>,
): String? {
    val topAffinities = adaptiveRankingRepository.topShowAffinities(limit = HOME_TOP_AFFINITIES_LIMIT)
    val candidates =
        HomeAnchorConfidenceLogic.toShowCandidates(
            topShowAffinities = topAffinities,
            hiddenPodcastIds = trigger.locallyHiddenPodcastIds,
            hardExcludedPodcastIds = hardExcluded,
        )
    val currentAnchorId = _seemsToLikePodcast.value?.id
    val selection =
        HomeAnchorSelectionLogic.selectAutomatic(
            candidates = candidates,
            currentAnchorId = currentAnchorId,
            manualOverrideId = trigger.manualAnchorOverride,
        )
    return selection?.podcastId
}

private suspend fun HomeViewModel.resolveAnchorPodcast(
    podcastId: String,
    subscribedPodcasts: List<Podcast>,
): Podcast? = subscribedPodcasts.firstOrNull { it.id == podcastId } ?: localCatalog.getLocalPodcast(podcastId)

private suspend fun HomeViewModel.resolveDiscoveryMission(daypart: ContentDaypart): HomeDiscoveryMission {
    val stickyMissionId = userPrefs.stickyMissionIdStream.first()
    val stickySlotKey = userPrefs.stickyMissionSlotKeyStream.first()
    val today = LocalDate.now().toString()
    val nowSlotKey =
        HomeDiscoveryMissionLogic.slotKey(
            daypart = HomeMissionContextLogic.daypartId(daypart),
            localDate = today,
        )
    val context =
        HomeMissionContextLogic.buildContext(
            daypart = daypart,
            stickyMissionId = stickyMissionId,
            stickySlotKey = stickySlotKey,
            nowSlotKey = nowSlotKey,
        )
    val mission =
        HomeDiscoveryMissionLogic.select(
            context = context,
            nowSlotKey = nowSlotKey,
            stickyMissionId = stickyMissionId,
            stickySlotKey = stickySlotKey,
        )
    userPrefs.setStickyMission(mission.id, nowSlotKey)
    return mission
}

private fun HomeViewModel.recordSlateExposures(
    taste: List<Episode>,
    becauseYouLike: List<Episode>,
    mission: List<Episode>,
) {
    viewModelScope.launch(Dispatchers.IO) {
        recordRailExposures(taste, exposureEntryPointBySurface.getValue("taste"))
        recordRailExposures(becauseYouLike, exposureEntryPointBySurface.getValue("because_you_like"))
        recordRailExposures(mission, exposureEntryPointBySurface.getValue("mission"))
    }
}

private suspend fun HomeViewModel.recordRailExposures(
    episodes: List<Episode>,
    entryPoint: String,
) {
    episodes.forEach { episode ->
        val exposureId =
            rankingFeedback.recordExposure(
                RankingExposure(
                    episodeId = episode.id,
                    podcastId = episode.podcastId.orEmpty(),
                    objective = RankingObjective.SLATE,
                    surface = RankingSurface.HOME,
                    source = CandidateSource.SERVER_RECOMMENDATION,
                    features =
                        CandidateFeatureBuilder.build(
                            CandidateSignals(
                                serverRelevance = (episode.retrievalScore ?: 0.0).coerceIn(0.0, 1.0),
                                isUnseenShow = true,
                                isUnplayed = true,
                            ),
                        ),
                    entryPoint = entryPoint,
                    online = true,
                    retrievalReason = episode.recommendationReason,
                ),
            )
        if (exposureId.isNotBlank()) {
            episodeExposureIds[episode.id] = exposureId
        }
    }
}

private fun HomeViewModel.cacheSlateResult(
    taste: List<Episode>,
    becauseYouLikeEpisodes: List<Episode>,
    becauseYouLikePodcasts: List<Podcast>,
    anchorPodcastId: String?,
    isFallback: Boolean,
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            boxcastPrefs.saveRecommendationsCache(json.encodeToString(taste), isFallback)
            if (anchorPodcastId != null) {
                boxcastPrefs.saveBylCache(
                    json.encodeToString(becauseYouLikeEpisodes),
                    json.encodeToString(becauseYouLikePodcasts),
                    anchorPodcastId,
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Failed to cache home slate", e)
        }
    }
}

/** Bumps the trigger revision so [observeHomePersonalization] restarts even when no other input changed. */
private fun HomeViewModel.bumpSlateRevision() {
    _slateRevision.update { it + 1 }
}

/**
 * Long-press feedback entry point for Taste / Because-You-Like / mission cards (see
 * [cx.aswin.boxlore.feature.home.components.RecommendationFeedbackMenu]).
 *
 * "Hide this show" removes the show from all three rails immediately (before the server confirms
 * the hard exclusion); "More like this" / "Not for me" record the signal and force a slate
 * reallocation on the next tick.
 */
fun HomeViewModel.onRecommendationFeedback(
    episodeId: String,
    podcastId: String,
    genre: String?,
    action: RecommendationFeedbackAction,
) {
    if (podcastId.isBlank()) return
    if (action == RecommendationFeedbackAction.HIDE_SHOW) {
        applyLocalHide(podcastId)
    }
    viewModelScope.launch {
        rankingFeedback.recordAction(
            target =
                FeedbackTarget(
                    episodeId = episodeId,
                    podcastId = podcastId,
                    genre = genre,
                    source = CandidateSource.SERVER_RECOMMENDATION,
                    exposureId = episodeExposureIds[episodeId],
                ),
            action = action.toRankingAction(),
        )
        if (action != RecommendationFeedbackAction.HIDE_SHOW) {
            bumpSlateRevision()
        }
    }
}

private fun RecommendationFeedbackAction.toRankingAction(): RankingAction =
    when (this) {
        RecommendationFeedbackAction.MORE_LIKE_THIS -> RankingAction.MORE_LIKE_THIS
        RecommendationFeedbackAction.NOT_FOR_ME -> RankingAction.NOT_FOR_ME
        RecommendationFeedbackAction.HIDE_SHOW -> RankingAction.HIDE_SHOW
    }

private fun HomeViewModel.applyLocalHide(podcastId: String) {
    val hidden =
        HomeFeedbackLogic.hideShowEverywhere(
            taste = _recommendations.value,
            becauseYouLikeEpisodes = _becauseYouLikeRecommendations.value,
            becauseYouLikePodcasts = _becauseYouLikePodcasts.value,
            missionEpisodes = _missionEpisodes.value,
            anchorPodcastId = _seemsToLikePodcast.value?.id,
            hiddenPodcastId = podcastId,
        )
    _recommendations.value = hidden.taste
    _becauseYouLikeRecommendations.value = hidden.becauseYouLikeEpisodes
    _becauseYouLikePodcasts.value = hidden.becauseYouLikePodcasts
    _missionEpisodes.value = hidden.missionEpisodes
    if (hidden.missionEpisodes.isEmpty()) {
        _activeMission.value = null
    }
    if (hidden.anchorCleared) {
        _seemsToLikePodcast.value = null
    }
    _locallyHiddenPodcastIds.update { it + podcastId }
}

/**
 * Builds the playback `sourceContext` extras carrying the Home slate's exact exposure token.
 * [EXPOSURE_ID_KEY] must match `PlaybackTelemetrySession.EXPOSURE_ID_KEY` (internal to
 * `:core:playback`, so feature modules mirror the plain string key like `"entry_point"` above).
 */
internal fun HomeViewModel.buildExposureSourceContext(
    episodeId: String,
    entryPoint: cx.aswin.boxlore.core.model.PlaybackEntryPoint,
): android.os.Bundle? {
    val exposureId = episodeExposureIds[episodeId] ?: return null
    return android.os.Bundle().apply {
        putString("entry_point", entryPoint.name.lowercase())
        putString(EXPOSURE_ID_KEY, exposureId)
    }
}

private const val EXPOSURE_ID_KEY = "exposure_id"
private const val HOME_SLATE_HISTORY_LIMIT = 15
private const val HOME_TOP_AFFINITIES_LIMIT = 20
