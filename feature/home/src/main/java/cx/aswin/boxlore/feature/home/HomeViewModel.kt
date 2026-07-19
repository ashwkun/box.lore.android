package cx.aswin.boxlore.feature.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.data.PodcastRepository
import cx.aswin.boxlore.core.data.content.AdaptiveContentCandidateRanker
import cx.aswin.boxlore.core.data.content.ContentContextEngine
import cx.aswin.boxlore.core.data.content.ContentOrchestrator
import cx.aswin.boxlore.core.data.content.ContentSection
import cx.aswin.boxlore.core.data.content.ContentSectionsDaypartResolver
import cx.aswin.boxlore.core.data.content.RecentSectionIntentStore
import cx.aswin.boxlore.core.data.content.ServerGroupedSectionProvider
import cx.aswin.boxlore.core.data.content.ServerIntentCandidateProvider
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import cx.aswin.boxlore.core.domain.ports.AlwaysOnlineConnectivity
import cx.aswin.boxlore.core.domain.ports.ConnectivityStatusPort
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.logic.FilterSelectionAction
import cx.aswin.boxlore.feature.home.logic.HomeBecauseYouLikeLogic
import cx.aswin.boxlore.feature.home.logic.HomeFilterSelectionLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json


@Suppress("kotlin:S6310", "LongParameterList")
class HomeViewModel(
    application: Application,
    val podcastRepository: PodcastRepository,
    internal val playbackRepository: cx.aswin.boxlore.core.playback.PlaybackRepository,
    internal val engagementCoordinator: cx.aswin.boxlore.core.data.EngagementPromptCoordinator,
    internal val subscriptionRepository: cx.aswin.boxlore.core.data.SubscriptionRepository,
    internal val downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository,
    internal val rssRepository: cx.aswin.boxlore.core.rss.RssPodcastRepository,
    internal val adaptiveRankingRepository: AdaptiveRankingRepository,
    internal val adaptiveScorer: AdaptiveCandidateScorer,
    internal val rankingFeedback: RankingFeedbackRepository,
    internal val localCatalog: cx.aswin.boxlore.core.domain.ports.LocalCatalogPort,
    internal val userPreferencesRepository: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    internal val connectivityStatus: ConnectivityStatusPort = AlwaysOnlineConnectivity,
) : AndroidViewModel(application) {
    val downloadedEpisodeIds: StateFlow<Set<String>> =
        downloadRepository.downloads
            .map { list -> list.map { it.episodeId }.toSet() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptySet(),
            )
    // Let's leave SubscriptionRepository as is for now, it's less critical for playback state.
    // But `playbackRepository` MUST be injected.

    internal val _uiState =
        MutableStateFlow(
            HomeUiState(
                heroItems = emptyList(),
                discoverPodcasts = emptyList(),
                isLoading = true,
            ),
        )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    internal val _showReviewPrompt = MutableStateFlow(false)
    val showReviewPrompt = _showReviewPrompt.asStateFlow()

    internal val _reviewPromptVariant =
        MutableStateFlow(cx.aswin.boxlore.feature.home.components.ReviewPromptVariant.Milestone)
    val reviewPromptVariant = _reviewPromptVariant.asStateFlow()

    internal val _showPostReview = MutableStateFlow(false)
    val showPostReview = _showPostReview.asStateFlow()

    internal val _showFeedback = MutableStateFlow(false)
    val showFeedback = _showFeedback.asStateFlow()

    internal val _selectedCategory = MutableStateFlow<String?>(null)
    internal val _recommendations = MutableStateFlow<List<Episode>>(emptyList())
    internal val _isTrendingLoaded = MutableStateFlow(false)
    internal val _isRecommendationsLoaded = MutableStateFlow(false)
    internal val _briefingState = MutableStateFlow<Briefing?>(null)
    internal val _briefingDismissedDate = MutableStateFlow("")
    internal val _briefingChaptersState = MutableStateFlow<List<cx.aswin.boxlore.core.model.Chapter>>(emptyList())
    internal val _briefingDismissedForever = MutableStateFlow(false)

    internal val _seemsToLikePodcast = MutableStateFlow<Podcast?>(null)
    internal val _becauseYouLikeRecommendations = MutableStateFlow<List<Episode>>(emptyList())
    internal val _becauseYouLikePodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    internal val _isBecauseYouLikeLoading = MutableStateFlow(false)
    internal val _isRecommendationsFallback = MutableStateFlow(true)
    internal val _adaptiveSections = MutableStateFlow<List<ContentSection>>(emptyList())
    internal val _isAdaptiveSectionsLoading = MutableStateFlow(true)

    @Volatile
    internal var preferCachedAdaptiveSections: Boolean = false
    internal val adaptiveContentSessionId =
        java.util.UUID
            .randomUUID()
            .toString()
    internal val exposedAdaptiveCandidates = mutableSetOf<String>()
    internal val exposedAdaptiveSectionIntents = mutableSetOf<String>()
    internal val recentSectionIntentStore = RecentSectionIntentStore(application)
    internal val contentContextEngine = ContentContextEngine()
    internal val clockContextFlow: StateFlow<HomeClockContext> =
        callbackFlow {
            fun currentClockContext(): HomeClockContext {
                val now = java.time.ZonedDateTime.now()
                val localMinuteOfDay = now.toLocalTime().let { it.hour * 60 + it.minute }
                return HomeClockContext(
                    daypart = contentContextEngine.currentDaypart(),
                    sectionDaypart = ContentSectionsDaypartResolver.resolve(localMinuteOfDay),
                    date = now.toLocalDate(),
                    timezoneOffsetMinutes = now.offset.totalSeconds / 60,
                )
            }
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        trySend(currentClockContext())
                    }
                }
            val filter =
                IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
            val context = getApplication<Application>()
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            trySend(currentClockContext())
            awaitClose { context.unregisterReceiver(receiver) }
        }.distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue =
                    java.time.ZonedDateTime.now().let { now ->
                        val time = now.toLocalTime()
                        HomeClockContext(
                            daypart = contentContextEngine.currentDaypart(),
                            sectionDaypart = ContentSectionsDaypartResolver.resolve(time.hour * 60 + time.minute),
                            date = now.toLocalDate(),
                            timezoneOffsetMinutes = now.offset.totalSeconds / 60,
                        )
                    },
            )
    internal val userPrefs = userPreferencesRepository
    internal val boxcastPrefs = BoxcastPrefs(application)
    internal val contentOrchestrator =
        ContentOrchestrator(
            providers =
                listOf(
                    ServerIntentCandidateProvider(podcastRepository),
                ),
            groupedProviders =
                listOf(
                    ServerGroupedSectionProvider { loadPersonalizedDiscoverySections(it) },
                ),
            ranker =
                AdaptiveContentCandidateRanker(adaptiveScorer) {
                    playbackRepository.getAllHistory().first()
                },
        )

    internal val allHomeHistory: Flow<List<HomeListeningHistoryItem>> =
        playbackRepository
            .getAllHistory()
            .map { history -> history.map { it.toHomeListeningHistoryItem() } }

    // Cached base data (For You)
    internal var cachedRegion: String? = null
    internal var cachedForYouTrending: List<Podcast> = emptyList()
    internal var cachedHeroItems: List<SmartHeroItem> = emptyList()
    internal var cachedLatestEpisodes: List<Podcast> = emptyList()
    internal var stablePodcastOrder: List<String>? = null
    internal var stableMixtapePodcasts: List<Podcast>? = null
    internal var stableMixtapeCount: Int? = null
    internal var stableCurrentUnplayedEpisodes: List<Episode>? = null

    // Signature of the subscription set the cached mixtape was built from. The mixtape is
    // intentionally NOT rebuilt on history/playback ticks (too churny), only when a show is
    // subscribed/unsubscribed so new shows appear promptly.
    internal var stableMixtapeSubSignature: Set<String>? = null

    // Subscription IDs we've already eagerly warmed episodes for (once per session).
    private val eagerlyLoadedSubIds =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())

    val lastSeenEpisodes: StateFlow<Map<String, String>> =
        userPrefs.lastSeenEpisodesStream
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyMap(),
            )

    fun markPodcastEpisodeAsSeen(
        podcastId: String,
        episodeId: String,
    ) {
        viewModelScope.launch {
            if (subscriptionRepository.isSubscribed(podcastId)) {
                userPrefs.setLastSeenEpisodeId(podcastId, episodeId)
                // Clears the RSS "new episodes" badge now that the user has opened/dismissed
                // this podcast; no-op for non-RSS podcasts.
                subscriptionRepository.clearRssNewEpisodesFlag(podcastId)
            }
        }
    }

    val candidatePodcasts: Flow<List<Podcast>> =
        combine(
            subscriptionRepository.subscribedPodcasts,
            allHomeHistory,
        ) { subs, history ->
            HomeBecauseYouLikeLogic.candidatePodcastsFromHistory(subs, history)
        }

    fun setOverriddenRecPodcast(podcastId: String?) {
        viewModelScope.launch {
            userPrefs.setOverriddenRecPodcastId(podcastId)
        }
    }

    // Expose region to UI
    val currentRegion = userPrefs.regionStream

    init {
        // Collect briefing dismiss state from DataStore
        viewModelScope.launch {
            userPrefs.briefingDismissedDate.collect { dismissedDate ->
                _briefingDismissedDate.value = dismissedDate
            }
        }
        viewModelScope.launch {
            userPrefs.briefingDismissedForever.collect { dismissedForever ->
                _briefingDismissedForever.value = dismissedForever
            }
        }

        // Load cached recommendations asynchronously on IO thread to prevent main-thread jank at startup
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Load cached recommendations
            try {
                val cached = boxcastPrefs.getCachedRecommendationsJson()
                if (cached != null) {
                    val json = Json { ignoreUnknownKeys = true }
                    val list = json.decodeFromString<List<Episode>>(cached)
                    _recommendations.value = list
                }
                // Load cached fallback flag
                _isRecommendationsFallback.value = boxcastPrefs.isRecommendationsFallback()
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to load cached recommendations", e)
            }

            // Load cached "Because You Like" recommendations
            try {
                val cached = boxcastPrefs.getCachedBylRecommendationsJson()
                val cachedPods = boxcastPrefs.getCachedBylPodcastsJson()
                val cachedPodId = boxcastPrefs.getCachedBylPodcastId()
                if (cachedPodId != null) {
                    val json = Json { ignoreUnknownKeys = true }
                    if (cached != null) {
                        val list = json.decodeFromString<List<Episode>>(cached)
                        _becauseYouLikeRecommendations.value = list
                    }
                    if (cachedPods != null) {
                        val podsList = json.decodeFromString<List<Podcast>>(cachedPods)
                        _becauseYouLikePodcasts.value = podsList
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to load cached because-you-like recommendations", e)
            }
        }

        // Lightweight RSS freshness checks never download or parse feed bodies.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                rssRepository.checkSubscribedFeedFreshness()
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to check subscribed feed freshness", e)
            }
        }

        // Start oldest-sort serial episode resolution after a delay
        startOldestSortResolution()

        // Observe overridden podcast ID and region, resolve favorite podcast, and fetch recommendations
        viewModelScope.launch {
            combine(
                userPrefs.overriddenRecPodcastIdStream,
                userPrefs.regionStream,
            ) { overriddenId, region ->
                overriddenId to region
            }.collectLatest { (overriddenId, region) ->
                val activeSubs = subscriptionRepository.subscribedPodcasts.first()
                val activeHistory = allHomeHistory.first()

                val resolvedPodcast = resolveFavoritePodcast(overriddenId, activeSubs, activeHistory)
                _seemsToLikePodcast.value = resolvedPodcast

                if (resolvedPodcast != null) {
                    fetchBecauseYouLikeRecommendations(resolvedPodcast, region)
                } else {
                    _becauseYouLikeRecommendations.value = emptyList()
                    _becauseYouLikePodcasts.value = emptyList()
                }
            }
        }
    }

    // Playback state to UI
    val playerState = playbackRepository.playerState

    // Store current region for use in other scopes
    internal var activeRegion = "us"

    internal val _selectedPodcastId = MutableStateFlow<String?>(null)
    internal val _selectedPodcastEpisodes = MutableStateFlow<List<Episode>>(emptyList())
    internal val _isSelectedPodcastLoading = MutableStateFlow(false)
    internal val _isSelectedRssRefreshing = MutableStateFlow(false)
    internal val _rssRefreshVersion = MutableStateFlow(0L)
    internal val rssFeedsRefreshedThisSession =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    internal var currentUnplayedEpisodes: List<Episode> = emptyList()
    internal val _resolvedSerialEpisodes = MutableStateFlow<Map<String, Episode>>(emptyMap())
    internal val inFlightResolutions = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    // True when the current filter selection was applied automatically (single-show state)
    // rather than by an explicit user tap. Used to decide whether to fall back to the mix
    // when the subscription set grows past one show.
    private var filterSelectionIsAuto = false

    fun selectPodcast(podcastId: String?) {
        applySelection(podcastId, isAuto = false)
    }

    private fun applySelection(
        podcastId: String?,
        isAuto: Boolean,
        autoResolvedPodcast: Podcast? = null,
    ) {
        filterSelectionIsAuto = isAuto && podcastId != null
        _selectedPodcastId.value = podcastId
        if (podcastId == null) return

        // Auto-selections resolve from the fresher subscriptionRepository list (passed in by the
        // caller) so a just-subscribed RSS podcast can trigger its refresh immediately, rather than
        // waiting for uiState to catch up. Manual selections keep resolving from uiState as before.
        when {
            autoResolvedPodcast != null ->
                applySelectedPodcast(podcastId, autoResolvedPodcast, isAuto)
            else ->
                uiState.value.subscribedPodcasts
                    .firstOrNull { it.id == podcastId }
                    ?.let { applySelectedPodcast(podcastId, it, isAuto) }
        }
    }

    private fun applySelectedPodcast(
        podcastId: String,
        podcast: Podcast,
        isAuto: Boolean,
    ) {
        // Auto-selection (single show) shouldn't be reported as a user-driven filter.
        if (!isAuto) {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackHomePodcastFiltered(podcastId, podcast.title)
        }

        // Mark as seen when filtered in "Your Shows"
        val latestEpisodeId = podcast.latestEpisode?.id
        if (latestEpisodeId != null) {
            markPodcastEpisodeAsSeen(podcastId, latestEpisodeId)
        }
        if (podcast.isRss) {
            val manualRefreshDue =
                podcast.rssRefreshCapability == Podcast.RSS_REFRESH_MANUAL &&
                    rssFeedsRefreshedThisSession.add(podcastId)
            if (podcast.rssCatalogStale || manualRefreshDue) {
                refreshSelectedRssPodcast(podcastId)
            }
        }
    }

    /**
     * Keeps the Home filter selection consistent as the subscription set changes:
     * - 0 shows → clear to the mix (nothing to show anyway).
     * - exactly 1 show → auto-select it; there is no mix to choose from.
     * - more than 1 show → default to the mix. Only clear an existing selection when it became
     *   invalid (the show was unsubscribed) or when it was auto-selected back when it was the
     *   sole show. Explicit user filters on a multi-show library are preserved.
     */
    private fun manageFilterSelectionOnSubscriptionChange() {
        viewModelScope.launch {
            subscriptionRepository.subscribedPodcasts
                .distinctUntilChanged { old, new -> old.map { it.id }.toSet() == new.map { it.id }.toSet() }
                .collect { subs -> handleSubscriptionSetChange(subs) }
        }
    }

    private fun handleSubscriptionSetChange(subs: List<Podcast>) {
        when (
            val action =
                HomeFilterSelectionLogic.decide(
                    currentSelectedId = _selectedPodcastId.value,
                    subs = subs,
                    filterSelectionIsAuto = filterSelectionIsAuto,
                )
        ) {
            FilterSelectionAction.Clear -> applySelection(null, isAuto = false)
            is FilterSelectionAction.AutoSelect ->
                applySelection(action.podcast.id, isAuto = true, autoResolvedPodcast = action.podcast)
            FilterSelectionAction.Keep -> Unit
        }
    }

    private fun refreshSelectedRssPodcast(podcastId: String) {
        viewModelScope.launch {
            try {
                rssRepository.refreshCatalog(podcastId).getOrThrow()
                _rssRefreshVersion.value += 1L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to refresh selected RSS podcast $podcastId", e)
            }
        }
    }

    fun playUnplayedMix() {
        val episodes = currentUnplayedEpisodes
        if (episodes.isEmpty()) return

        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .trackPlayMixClicked(episodes.size)

        val dummyPodcast =
            Podcast(
                id = "unplayed_mix",
                title = "Your Shows Mix",
                artist = "Various Artists",
                imageUrl = "",
                fallbackImageUrl = null,
                description = "Mixed playlist of unplayed episodes",
                genre = "Mix",
            )
        viewModelScope.launch {
            playbackRepository.playQueue(episodes, dummyPodcast, 0)
        }
    }

    fun playEpisode(
        episode: Episode,
        podcast: Podcast,
        entryPoint: cx.aswin.boxlore.core.model.PlaybackEntryPoint = cx.aswin.boxlore.core.model.PlaybackEntryPoint.GENERIC,
    ) {
        viewModelScope.launch {
            if (playbackRepository.playerState.value.currentEpisode
                    ?.id == episode.id
            ) {
                playbackRepository.togglePlayPause()
            } else {
                playbackRepository.playQueue(listOf(episode), podcast, 0, entryPoint)
            }
        }
    }




    init {
        observeSelectedPodcast()
        manageFilterSelectionOnSubscriptionChange()
        observeDiscoveryGreeting()
        loadData()
        loadAdaptiveContent()
        startBackgroundSync()
        eagerlyLoadNewSubscriptions()
    }

    private fun observeDiscoveryGreeting() {
        viewModelScope.launch {
            clockContextFlow
                .map { clock -> clock.daypart to clock.date }
                .distinctUntilChanged()
                .collect { (daypart, date) ->
                    _uiState.update { state ->
                        state.copy(discoveryGreeting = discoveryGreetingFor(daypart, date))
                    }
                }
        }
    }











    fun setRegion(region: String) {
        viewModelScope.launch {
            userPrefs.setRegion(region)
        }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .trackDiscoverCategoryFiltered(category ?: "All")
    }

    fun toggleSubscription(podcastId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val podcast =
                state.adaptiveSections
                    .asSequence()
                    .flatMap { it.items.asSequence() }
                    .map { it.podcast }
                    .firstOrNull { it.id == podcastId }
                    ?: state.discoverPodcasts.find { it.id == podcastId }
                    ?: state.heroItems.find { it.podcast.id == podcastId }?.podcast

            if (podcast != null) {
                subscriptionRepository.toggleSubscription(podcast)
                val isSubscribed = subscriptionRepository.isSubscribed(podcast.id)

                if (isSubscribed) {
                    // Fetch latest episodes for the newly subscribed podcast so UI updates immediately
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val synced = podcastRepository.syncSubscriptions(listOf(podcast.id))
                            synced[podcast.id]?.let { episode ->
                                subscriptionRepository.updateLatestEpisode(podcast.id, episode)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    userPrefs.removeLastSeenEpisodeId(podcastId)
                }
            }
        }
    }

    fun togglePlayback(entryPointContext: android.os.Bundle? = null) {
        playbackRepository.togglePlayPause(entryPointContext)
    }

    fun dismissReviewPrompt() {
        _showReviewPrompt.value = false
        _reviewPromptVariant.value =
            cx.aswin.boxlore.feature.home.components.ReviewPromptVariant.Milestone
    }

    fun dismissPostReview() {
        _showPostReview.value = false
    }

    fun dismissFeedback() {
        _showFeedback.value = false
    }

    fun triggerFeedback() {
        _showReviewPrompt.value = false
        _showPostReview.value = false
        _showFeedback.value = true
    }

    /** Debug: Force-show the review prompt (long-press feedback icon) */
    fun forceReviewPrompt() {
        _showFeedback.value = false
        _showPostReview.value = false
        _showReviewPrompt.value = true
    }

    /** Manually trigger the NPS survey (long-press feedback icon). Bypasses the ep-3 milestone. */
    fun forceSurveyNps() {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .trackSurveyNpsManualTrigger(source = "long_press")
    }

    fun triggerPostReview() {
        _showReviewPrompt.value = false
        _showPostReview.value = true
    }

    fun markReviewPromptShown() {
        viewModelScope.launch {
            userPrefs.markReviewPromptShown()
            _showReviewPrompt.value = false
        }
    }

    fun markReviewed() {
        viewModelScope.launch {
            userPrefs.markReviewed()
            _showReviewPrompt.value = false
            _showPostReview.value = true
        }
    }

    /**
     * Warms episode data for shows subscribed *during* this session so a freshly added show has
     * content ready for the mixtape and the Home filter view immediately — without waiting for the
     * periodic background sync. Existing subs (present on first emission) are handled by
     * [startBackgroundSync]; this only reacts to genuinely new additions.
     */
    private fun eagerlyLoadNewSubscriptions() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var seenFirstEmission = false
            subscriptionRepository.subscribedPodcasts.collect { subs ->
                if (!seenFirstEmission) {
                    seenFirstEmission = true
                    subs.forEach { eagerlyLoadedSubIds.add(it.id) }
                    return@collect
                }
                val newlyAdded = subs.filter { eagerlyLoadedSubIds.add(it.id) }
                if (newlyAdded.isEmpty()) return@collect

                for (pod in newlyAdded) {
                    warmUpNewSubscription(pod)
                }
            }
        }
    }

    /**
     * Warms a single freshly-subscribed show: tops up its RSS catalog or syncs its latest
     * episode, so it's ready for the mixtape and Home filter view without waiting for the next
     * periodic [startBackgroundSync]. Failures are logged and swallowed per-podcast so one bad
     * feed doesn't stop the rest of the batch in [eagerlyLoadNewSubscriptions].
     */
    private suspend fun warmUpNewSubscription(pod: Podcast) {
        try {
            if (pod.isRss) {
                // RSS catalogs are stored at subscribe time; this tops up latest
                // episodes cheaply (HEAD-gated) so the filter view is fresh.
                rssRepository.refreshCatalogIfNeeded(pod.id).getOrThrow()
            } else if (pod.latestEpisode == null) {
                val synced = podcastRepository.syncSubscriptions(listOf(pod.id))
                for ((podId, episode) in synced) {
                    subscriptionRepository.updateLatestEpisode(podId, episode)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e(
                "HomeViewModel",
                "Eager load for new sub ${pod.id} failed",
                e,
            )
        }
    }

    private fun startBackgroundSync() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Wait slightly so the app completely loads up the UI before choking up network requests
            kotlinx.coroutines.delay(2000L)

            try {
                // Get all subscribed podcast IDs from repository
                val currentSubs = subscriptionRepository.subscribedPodcastIds.first()
                if (currentSubs.isEmpty()) return@launch

                val chunks = currentSubs.chunked(10) // Chunk by 10 per user request
                android.util.Log.d("HomeViewModel", "Starting background sync for ${currentSubs.size} subs in ${chunks.size} chunks")
                for (chunk in chunks) {
                    try {
                        val synced = podcastRepository.syncSubscriptions(chunk.toList())
                        android.util.Log.d("HomeViewModel", "Successfully fetched chunk of ${chunk.size} subs, saving to DB...")
                        for ((podId, episode) in synced) {
                            subscriptionRepository.updateLatestEpisode(podId, episode)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Background sync chunk failed", e)
                    }
                }
                android.util.Log.d("HomeViewModel", "Finished background sync for all ${currentSubs.size} subs")
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Background sync failed totally", e)
            }
        }
    }

    fun dismissHomeImportBanner() {
        viewModelScope.launch {
            userPrefs.dismissHomeImportBanner()
        }
    }

    fun dismissBriefingForToday() {
        val currentBriefing = _briefingState.value
        if (currentBriefing != null) {
            viewModelScope.launch {
                userPrefs.dismissBriefing(currentBriefing.date)
            }
        }
    }

    fun dismissBriefingForever() {
        viewModelScope.launch {
            userPrefs.dismissBriefingForever()
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

