package cx.aswin.boxcast.feature.home

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import androidx.core.os.bundleOf

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.HomeBootstrapData
import cx.aswin.boxcast.core.data.PodcastScoring
import cx.aswin.boxcast.core.data.toScorable
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.EpisodeStatus
import kotlinx.coroutines.flow.Flow
import cx.aswin.boxcast.core.data.database.ListeningHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Briefing
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.awaitAll
import java.util.Calendar
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Bedtime

private const val MAX_MIXTAPE_ITEMS = 15
private const val MIXTAPE_AFFINITY_WEIGHT = 0.8

private data class MixtapeCandidate(
    val episodeId: String,
    val score: Double,
    val isProgress: Boolean,
    val podcast: Podcast,
    val episode: Episode,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L
)

@Immutable
data class SmartHeroItem(
    val type: HeroType,
    val podcast: Podcast,
    val label: String,
    val description: String? = null,
    val gridItems: List<Podcast> = emptyList() // For RESUME_GRID
)


enum class HeroType { RESUME, RESUME_GRID, JUMP_BACK_IN, NEW_EPISODES_GRID, SPOTLIGHT }

@Immutable
data class CuratedTimeBlock(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val sections: List<CuratedSectionData>
)

@Immutable
data class CuratedSectionData(
    val title: String, 
    val category: String,
    val podcasts: List<Podcast>
)

@Immutable
data class HomeUiState(
    val heroItems: List<SmartHeroItem>,
    val latestEpisodes: List<Podcast> = emptyList(), // "Latest" Section (Smart: Unplayed → In Progress → Completed)
    val unplayedEpisodeCount: Int = 0, // Badge count for "New Episodes" header
    val completedEpisodeCount: Int = 0, 
    val subscribedPodcasts: List<Podcast> = emptyList(), // "Your Shows" Section
    val selectedCategory: String? = null, // Null = "For You"
    val timeBlock: CuratedTimeBlock? = null, // Unified Time Block
    val discoverPodcasts: List<Podcast>, 
    val recommendations: List<Episode> = emptyList(), // Personalized recommendations from Qdrant
    val isLoading: Boolean = false, // Initial full-screen loader
    val isFilterLoading: Boolean = false, // Inline loader when switching genres
    val isError: Boolean = false,
    val showRegionNudge: Boolean = false,
    val systemRegionCode: String = "",
    val activeRegionCode: String = "",
    val selectedPodcastId: String? = null,
    val selectedPodcastEpisodes: List<Episode> = emptyList(),
    val isSelectedPodcastLoading: Boolean = false,
    val episodePlaybackState: Map<String, Pair<EpisodeStatus, Float>> = emptyMap(),
    val showImportBanner: Boolean = false,
    val briefing: Briefing? = null,
    val briefingChapters: List<cx.aswin.boxcast.core.model.Chapter> = emptyList(),
    val isRecommendationsLoading: Boolean = true,
    val isCuratedLoading: Boolean = true,
    val seemsToLikePodcast: Podcast? = null,
    val becauseYouLikeRecommendations: List<Episode> = emptyList(),
    val becauseYouLikePodcasts: List<Podcast> = emptyList(),
    val isBecauseYouLikeLoading: Boolean = false,
    val isRecommendationsFallback: Boolean = true
)

data class HomeDataWrapper(
    val trending: List<Podcast>,
    val resume: List<cx.aswin.boxcast.core.data.PlaybackSession>,
    val subs: List<Podcast>,
    val history: List<cx.aswin.boxcast.core.data.database.ListeningHistoryEntity>,
    val resolvedSerial: Map<String, Episode>,
    val recommendations: List<Episode> = emptyList(),
    val completedEpisodeIds: Set<String> = emptySet(),
    val isTrendingLoaded: Boolean = false,
    val isCuratedLoaded: Boolean = false,
    val isRecommendationsLoaded: Boolean = false,
    val hasDismissedImportBanner: Boolean = false,
    val briefing: Briefing? = null,
    val briefingChapters: List<cx.aswin.boxcast.core.model.Chapter> = emptyList(),
    val briefingDismissedDate: String = "",
    val briefingDismissedForever: Boolean = false,
    val hasDismissedRegionNudge: Boolean = false,
    val seemsToLikePodcast: Podcast? = null,
    val becauseYouLikeRecommendations: List<Episode> = emptyList(),
    val becauseYouLikePodcasts: List<Podcast> = emptyList(),
    val isBecauseYouLikeLoading: Boolean = false,
    val isRecommendationsFallback: Boolean = true
)

/**
 * Shared switching state observable from any composable.
 * Used by MainActivity to hide the mini player during mode switch animation.
 */
object ModeSwitchState {
    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()
    
    fun start() { _isSwitching.value = true }
    fun finish() { _isSwitching.value = false }
}


@Suppress("kotlin:S6310")
class HomeViewModel(
    application: Application,
    apiBaseUrl: String,
    publicKey: String,
    private val playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository
) : AndroidViewModel(application) {
    
    val podcastRepository = PodcastRepository(baseUrl = apiBaseUrl, publicKey = publicKey, context = application)
    private val database = cx.aswin.boxcast.core.data.database.BoxLoreDatabase.getDatabase(application)
    private val subscriptionRepository = cx.aswin.boxcast.core.data.SubscriptionRepository(database.podcastDao())
    private val downloadRepository = cx.aswin.boxcast.core.data.DownloadRepository(application, database)
    val downloadedEpisodeIds: StateFlow<Set<String>> = downloadRepository.downloads
        .map { list -> list.map { it.episodeId }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )
    // Let's leave SubscriptionRepository as is for now, it's less critical for playback state.
    // But `playbackRepository` MUST be injected.

    private val _uiState = MutableStateFlow(HomeUiState(emptyList(), emptyList(), 0, 0, emptyList(), null, null, emptyList(), isLoading = true, isFilterLoading = false))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _showReviewPrompt = MutableStateFlow(false)
    val showReviewPrompt = _showReviewPrompt.asStateFlow()

    private val _showPostReview = MutableStateFlow(false)
    val showPostReview = _showPostReview.asStateFlow()

    private val _showFeedback = MutableStateFlow(false)
    val showFeedback = _showFeedback.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _recommendations = MutableStateFlow<List<Episode>>(emptyList())
    private val _timeBlockState = MutableStateFlow<CuratedTimeBlock?>(null)
    
    private val _isTrendingLoaded = MutableStateFlow(false)
    private val _isCuratedLoaded = MutableStateFlow(false)
    private val _isRecommendationsLoaded = MutableStateFlow(false)
    private val _briefingState = MutableStateFlow<Briefing?>(null)
    private val _briefingDismissedDate = MutableStateFlow("")
    private val _briefingChaptersState = MutableStateFlow<List<cx.aswin.boxcast.core.model.Chapter>>(emptyList())
    private val _briefingDismissedForever = MutableStateFlow(false)
    
    private val _seemsToLikePodcast = MutableStateFlow<Podcast?>(null)
    private val _becauseYouLikeRecommendations = MutableStateFlow<List<Episode>>(emptyList())
    private val _becauseYouLikePodcasts = MutableStateFlow<List<Podcast>>(emptyList())
    private val _isBecauseYouLikeLoading = MutableStateFlow(false)
    private val _isRecommendationsFallback = MutableStateFlow(true)

    // Cached base data (For You)
    private var cachedRegion: String? = null
    private var cachedForYouTrending: List<Podcast> = emptyList()
    private var cachedHeroItems: List<SmartHeroItem> = emptyList()
    private var cachedTimeBlock: CuratedTimeBlock? = null
    private var cachedLatestEpisodes: List<Podcast> = emptyList()
    private var stablePodcastOrder: List<String>? = null
    private var stableMixtapePodcasts: List<Podcast>? = null
    private var stableMixtapeCount: Int? = null
    private var stableCurrentUnplayedEpisodes: List<Episode>? = null

    private val userPrefs = cx.aswin.boxcast.core.data.UserPreferencesRepository(application)

    val lastSeenEpisodes: StateFlow<Map<String, String>> = userPrefs.lastSeenEpisodesStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun markPodcastEpisodeAsSeen(podcastId: String, episodeId: String) {
        viewModelScope.launch {
            if (subscriptionRepository.isSubscribed(podcastId)) {
                userPrefs.setLastSeenEpisodeId(podcastId, episodeId)
            }
        }
    }

    val candidatePodcasts: Flow<List<Podcast>> = combine(
        subscriptionRepository.subscribedPodcasts,
        playbackRepository.getAllHistory()
    ) { subs, history ->
        val playedPods = history.distinctBy { it.podcastId }.map { h ->
            Podcast(
                id = h.podcastId,
                title = h.podcastName,
                artist = "",
                imageUrl = h.podcastImageUrl ?: "",
                fallbackImageUrl = "",
                description = ""
            )
        }.filter { it.id.isNotEmpty() }
        (subs + playedPods).distinctBy { it.id }
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

        // Load cached recommendations and curated vibes asynchronously on IO thread to prevent main-thread jank at startup
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Load cached recommendations
            try {
                val prefs = application.getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
                val cached = prefs.getString("cached_recommendations", null)
                if (cached != null) {
                    val json = Json { ignoreUnknownKeys = true }
                    val list = json.decodeFromString<List<Episode>>(cached)
                    _recommendations.value = list
                }
                // Load cached fallback flag
                _isRecommendationsFallback.value = prefs.getBoolean("is_recommendations_fallback", true)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to load cached recommendations", e)
            }

            // Load cached "Because You Like" recommendations
            try {
                val prefs = application.getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
                val cached = prefs.getString("cached_byl_recommendations", null)
                val cachedPods = prefs.getString("cached_byl_podcasts", null)
                val cachedPodId = prefs.getString("cached_byl_podcast_id", null)
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

            // Load cached Curated Vibes
            try {
                val prefs = application.getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
                val cachedVibesJson = prefs.getString("cached_curated_vibes", null)
                if (cachedVibesJson != null) {
                    val json = Json { ignoreUnknownKeys = true }
                    val cachedVibesMap = json.decodeFromString<Map<String, List<Podcast>>>(cachedVibesJson)
                    val blockConfig = getTimeBlockConfig()
                    val daySeed = java.time.LocalDate.now().toEpochDay()
                    val resolvedSections = blockConfig.genres.map { genre ->
                        val list = cachedVibesMap[genre.id] ?: emptyList()
                        val filtered = list
                            .filter { it.latestEpisode != null }
                            .shuffled(kotlin.random.Random(daySeed.toInt() + genre.title.hashCode()))
                            .take(10)
                        if (filtered.isNotEmpty()) {
                            CuratedSectionData(genre.title, genre.id, filtered)
                        } else null
                    }.filterNotNull()

                    if (resolvedSections.isNotEmpty()) {
                        val block = CuratedTimeBlock(blockConfig.title, blockConfig.subtitle, blockConfig.icon, resolvedSections)
                        _timeBlockState.value = block
                        cachedTimeBlock = block
                        _isCuratedLoaded.value = true
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to load cached curated vibes", e)
            }
        }

        // Start oldest-sort serial episode resolution after a delay
        startOldestSortResolution()

        // Observe overridden podcast ID and region, resolve favorite podcast, and fetch recommendations
        viewModelScope.launch {
            combine(
                userPrefs.overriddenRecPodcastIdStream,
                userPrefs.regionStream
            ) { overriddenId, region ->
                overriddenId to region
            }.collectLatest { (overriddenId, region) ->
                val activeSubs = subscriptionRepository.subscribedPodcasts.first()
                val activeHistory = playbackRepository.getAllHistory().first()
                
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
    
    // Curated impression dedup — prevents LazyGrid recomposition from re-firing
    private var hasFiredCuratedImpression = false
    
    fun trackCuratedImpressionOnce(blockTitle: String, vibeIds: List<String>) {
        if (hasFiredCuratedImpression) return
        hasFiredCuratedImpression = true
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackCuratedBlockImpression(blockTitle, vibeIds)
    }
    
    // Store current region for use in other scopes
    private var activeRegion = "us"

    private val _selectedPodcastId = MutableStateFlow<String?>(null)
    private val _selectedPodcastEpisodes = MutableStateFlow<List<Episode>>(emptyList())
    private val _isSelectedPodcastLoading = MutableStateFlow(false)
    private var currentUnplayedEpisodes: List<Episode> = emptyList()
    private val _resolvedSerialEpisodes = MutableStateFlow<Map<String, Episode>>(emptyMap())
    private val inFlightResolutions = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun selectPodcast(podcastId: String?) {
        _selectedPodcastId.value = podcastId
        if (podcastId != null) {
            val podcast = uiState.value.subscribedPodcasts.find { it.id == podcastId }
            val title = podcast?.title ?: ""
            cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackHomePodcastFiltered(podcastId, title)
            
            // Mark as seen when filtered in "Your Shows"
            podcast?.latestEpisode?.id?.let { episodeId ->
                markPodcastEpisodeAsSeen(podcastId, episodeId)
            }
        }
    }

    fun playUnplayedMix() {
        val episodes = currentUnplayedEpisodes
        if (episodes.isEmpty()) return
        
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackPlayMixClicked(episodes.size)
        
        val dummyPodcast = Podcast(
            id = "unplayed_mix",
            title = "Your Shows Mix",
            artist = "Various Artists",
            imageUrl = "",
            fallbackImageUrl = null,
            description = "Mixed playlist of unplayed episodes",
            genre = "Mix"
        )
        viewModelScope.launch {
            playbackRepository.playQueue(episodes, dummyPodcast, 0)
        }
    }

    fun playEpisode(episode: Episode, podcast: Podcast, entryPoint: cx.aswin.boxcast.core.model.PlaybackEntryPoint = cx.aswin.boxcast.core.model.PlaybackEntryPoint.GENERIC) {
        viewModelScope.launch {
            if (playbackRepository.playerState.value.currentEpisode?.id == episode.id) {
                playbackRepository.togglePlayPause()
            } else {
                playbackRepository.playQueue(listOf(episode), podcast, 0, entryPoint)
            }
        }
    }

    private fun startOldestSortResolution() {
        viewModelScope.launch {
            // 3-second startup delay
            kotlinx.coroutines.delay(3000)
            
            // Combine subscribed podcasts and history to watch for changes
            combine(
                subscriptionRepository.subscribedPodcasts,
                playbackRepository.getAllHistory(),
                playbackRepository.completedEpisodeIds
            ) { subs, allHistory, completedEpisodeIds ->
                Triple(subs, allHistory, completedEpisodeIds)
            }.collect { (subs, allHistory, completedEpisodeIds) ->
                val completedEpIdsForResolve = allHistory.filter { it.isCompleted }.map { it.episodeId }.toSet() + completedEpisodeIds
                val inProgressEpIdsForResolve = allHistory.filter { !it.isCompleted && it.progressMs > 0L }.map { it.episodeId }.toSet()

                val serialPodsToResolve = findPendingSerialPodcasts(subs, allHistory, completedEpisodeIds)

                if (serialPodsToResolve.isNotEmpty()) {
                    serialPodsToResolve.forEach { inFlightResolutions.add(it.id) }
                    
                    // Parallel resolution using async/awaitAll
                    launch(kotlinx.coroutines.Dispatchers.Default) {
                        try {
                            val deferredList = serialPodsToResolve.map { pod ->
                                async {
                                    try {
                                        val ongoingId = allHistory.filter { h -> h.podcastId == pod.id && !h.isCompleted && h.progressMs > 0L }.maxByOrNull { it.lastPlayedAt }?.episodeId
                                        val lastCompletedId = allHistory.filter { h -> h.podcastId == pod.id && h.isCompleted }.maxByOrNull { it.lastPlayedAt }?.episodeId
                                        
                                        android.util.Log.d("HomeViewModelResolve", "Resolving pod=${pod.title} id=${pod.id} ongoingId=$ongoingId lastCompletedId=$lastCompletedId")
                                        
                                        // Fetch all episodes chronologically oldest to newest
                                        val page = podcastRepository.getEpisodesPaginated(pod.id, limit = 200, offset = 0, sort = "oldest")
                                        val allEpisodes = page.episodes
                                        
                                        val nextEp = resolveNextSerialEpisode(
                                            allEpisodes = allEpisodes,
                                            ongoingId = ongoingId,
                                            lastCompletedId = lastCompletedId,
                                            completedEpIdsForResolve = completedEpIdsForResolve,
                                            inProgressEpIdsForResolve = inProgressEpIdsForResolve
                                        )
                                        
                                        if (nextEp != null) {
                                            pod.id to nextEp
                                        } else {
                                            null
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("HomeViewModel", "Failed to resolve next episode for serial pod ${pod.id}", e)
                                        null
                                    }
                                }
                            }
                            
                            val results = deferredList.awaitAll().filterNotNull()
                            if (results.isNotEmpty()) {
                                _resolvedSerialEpisodes.update { current ->
                                    current + results
                                }
                            }
                        } finally {
                            serialPodsToResolve.forEach { inFlightResolutions.remove(it.id) }
                        }
                    }
                }
            }
        }
    }

    private fun findPendingSerialPodcasts(
        subs: List<Podcast>,
        allHistory: List<cx.aswin.boxcast.core.data.database.ListeningHistoryEntity>,
        completedEpisodeIds: Set<String>
    ): List<Podcast> {
        val completedEpIdsForResolve = allHistory.filter { it.isCompleted }.map { it.episodeId }.toSet() + completedEpisodeIds
        val inProgressEpIdsForResolve = allHistory.filter { !it.isCompleted && it.progressMs > 0L }.map { it.episodeId }.toSet()

        return subs.filter { (it.preferredSort ?: "newest") == "oldest" }.filter { pod ->
            val currentResolved = _resolvedSerialEpisodes.value[pod.id]
            val needsResolve = currentResolved == null || currentResolved.id in completedEpIdsForResolve || currentResolved.id in inProgressEpIdsForResolve
            needsResolve && pod.id !in inFlightResolutions
        }
    }

    private fun resolveNextSerialEpisode(
        allEpisodes: List<Episode>,
        ongoingId: String?,
        lastCompletedId: String?,
        completedEpIdsForResolve: Set<String>,
        inProgressEpIdsForResolve: Set<String>
    ): Episode? {
        val nextEp = when {
            ongoingId != null -> {
                val ongoingIndex = allEpisodes.indexOfFirst { it.id == ongoingId }
                if (ongoingIndex != -1 && ongoingIndex < allEpisodes.lastIndex) {
                    allEpisodes[ongoingIndex + 1]
                } else {
                    null
                }
            }
            lastCompletedId != null -> {
                val completedIndex = allEpisodes.indexOfFirst { it.id == lastCompletedId }
                if (completedIndex != -1 && completedIndex < allEpisodes.lastIndex) {
                    allEpisodes[completedIndex + 1]
                } else {
                    null
                }
            }
            else -> {
                allEpisodes.firstOrNull()
            }
        }
        return nextEp ?: allEpisodes.firstOrNull { ep ->
            ep.id !in completedEpIdsForResolve && ep.id !in inProgressEpIdsForResolve
        }
    }

    private fun observeSelectedPodcast() {
        viewModelScope.launch {
            // Derive a distinct signal from history & subscriptions: (podcastId, lastPlayedEpisodeId, preferredSort) for the selected podcast.
            // This only changes when the selected podcast changes, the last played episode ID changes, or the subscription preferredSort changes.
            val historySignal = combine(
                _selectedPodcastId,
                playbackRepository.getAllHistory(),
                subscriptionRepository.subscribedPodcasts
            ) { podcastId, allHistory, subs ->
                if (podcastId == null) {
                    null
                } else {
                    val lastPlayed = allHistory
                        .filter { it.podcastId == podcastId }
                        .maxByOrNull { it.lastPlayedAt }
                    val podcast = subs.find { it.id == podcastId }
                    val preferredSort = podcast?.preferredSort ?: "newest"
                    Triple(podcastId, lastPlayed?.episodeId, preferredSort)
                }
            }.distinctUntilChanged()

            historySignal.collectLatest { info ->
                if (info == null) {
                    android.util.Log.d("HomeViewModelFilteredView", "historySignal collected: null (clearing selected podcast episodes)")
                    _selectedPodcastEpisodes.value = emptyList()
                    _isSelectedPodcastLoading.value = false
                } else {
                    val (podcastId, lastPlayedEpisodeId, sort) = info
                    android.util.Log.d(
                        "HomeViewModelFilteredView",
                        "historySignal collected: podcastId=$podcastId, lastPlayedEpisodeId=$lastPlayedEpisodeId, sort=$sort"
                    )
                    _isSelectedPodcastLoading.value = true
                    try {
                        if (sort == "oldest") {
                            // Fetch all episodes of the podcast (up to 500) to find the index of the last played episode
                            val page = podcastRepository.getEpisodesPaginated(podcastId, limit = 500, offset = 0, sort = "oldest")
                            val allEpisodes = page.episodes
                            
                            val lastPlayedIndex = if (lastPlayedEpisodeId != null) {
                                allEpisodes.indexOfFirst { it.id == lastPlayedEpisodeId }
                            } else -1
                            
                            val offset = if (lastPlayedIndex != -1) {
                                (lastPlayedIndex - 2).coerceAtLeast(0)
                            } else 0
                            
                            val selectedRaw = allEpisodes.drop(offset).take(15)
                            android.util.Log.d(
                                "HomeViewModelFilteredView",
                                "Oldest sort resolution for podcastId=$podcastId: " +
                                "totalEpisodesFetched=${allEpisodes.size}, " +
                                "lastPlayedIndex=$lastPlayedIndex, " +
                                "calculatedOffset=$offset, " +
                                "rawEpisodesSelectedCount=${selectedRaw.size}"
                            )
                            selectedRaw.forEachIndexed { index, ep ->
                                android.util.Log.d(
                                    "HomeViewModelFilteredView",
                                    "  Raw Episode[$index]: id=${ep.id}, title=${ep.title}, pubDate=${ep.publishedDate}"
                                )
                            }
                            _selectedPodcastEpisodes.value = selectedRaw
                        } else {
                            val page = podcastRepository.getEpisodesPaginated(podcastId, limit = 25, offset = 0, sort = "newest")
                            val selectedRaw = page.episodes
                            android.util.Log.d(
                                "HomeViewModelFilteredView",
                                "Newest sort resolution for podcastId=$podcastId: " +
                                "rawEpisodesSelectedCount=${selectedRaw.size}"
                            )
                            selectedRaw.forEachIndexed { index, ep ->
                                android.util.Log.d(
                                    "HomeViewModelFilteredView",
                                    "  Raw Episode[$index]: id=${ep.id}, title=${ep.title}, pubDate=${ep.publishedDate}"
                                )
                            }
                            _selectedPodcastEpisodes.value = selectedRaw
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModelFilteredView", "Failed to fetch episodes for filter: $podcastId", e)
                        _selectedPodcastEpisodes.value = emptyList()
                    } finally {
                        _isSelectedPodcastLoading.value = false
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(
                _selectedPodcastId,
                _selectedPodcastEpisodes,
                _isSelectedPodcastLoading,
                userPrefs.hideCompletedInHomeStream,
                playbackRepository.completedEpisodeIds
            ) { id, eps, loading, hideCompleted, completedIds ->
                android.util.Log.d(
                    "HomeViewModelFilteredView",
                    "Filtering combine triggered for podcastId=$id: " +
                    "epsCount=${eps.size}, hideCompleted=$hideCompleted, " +
                    "totalCompletedIdsCount=${completedIds.size}"
                )
                val filteredEps = if (hideCompleted) {
                    eps.filter { it.id !in completedIds }
                } else {
                    eps
                }
                
                android.util.Log.d(
                    "HomeViewModelFilteredView",
                    "Filter result: rawCount=${eps.size} -> filteredCount=${filteredEps.size}"
                )
                eps.forEachIndexed { index, ep ->
                    val isCompleted = ep.id in completedIds
                    val wasKept = !hideCompleted || !isCompleted
                    android.util.Log.d(
                        "HomeViewModelFilteredView",
                        "  Episode[$index]: id=${ep.id}, title=${ep.title}, isCompleted=$isCompleted, wasKept=$wasKept"
                    )
                }
                
                Triple(id, filteredEps, loading)
            }.collect { (id, eps, loading) ->
                _uiState.update { it.copy(
                    selectedPodcastId = id,
                    selectedPodcastEpisodes = eps,
                    isSelectedPodcastLoading = loading
                ) }
            }
        }
    }

    init {
        android.util.Log.d("BoxCastPerf", "PERF: HomeViewModel init started")
        observeSelectedPodcast()
        loadData()
        startBackgroundSync()
    }

    private fun fetchPersonalizedRecommendations(region: String) {
        viewModelScope.launch {
            _isRecommendationsLoaded.value = false
            try {
                val prefs = getApplication<Application>().getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
                val interests = prefs.getStringSet("user_genres", emptySet())?.toList() ?: emptyList()
                val history = playbackRepository.getHistoryForRecommendations(15)
                
                val subscribedIds = subscriptionRepository.subscribedPodcastIds.first().toList()
                val subscribedGenres = subscriptionRepository.subscribedPodcasts.first()
                    .mapNotNull { it.genre }
                    .distinct()
                
                android.util.Log.d("HomeViewModel", "Fetching recommendations with history size: ${history.size}, interests: $interests, region: $region, subscribedCount: ${subscribedIds.size}")
                val recs = podcastRepository.getPersonalizedRecommendations(
                    history = history,
                    interests = interests,
                    country = region,
                    subscribedPodcastIds = subscribedIds,
                    subscribedGenres = subscribedGenres
                )
                android.util.Log.d("HomeViewModel", "Fetched recommendations size: ${recs.size}")
                val distinctRecs = recs
                    .distinctBy { it.id }
                    .distinctBy { it.title.lowercase().trim() }
                _recommendations.value = distinctRecs
                try {
                    val json = Json { ignoreUnknownKeys = true }
                    val serialized = json.encodeToString(distinctRecs)
                    prefs.edit().putString("cached_recommendations", serialized).apply()
                } catch (ce: Exception) {
                    android.util.Log.e("HomeViewModel", "Failed to cache recommendations", ce)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to fetch personalized recommendations", e)
            } finally {
                _isRecommendationsLoaded.value = true
            }
        }
    }



    private fun loadData() {
        viewModelScope.launch {
            android.util.Log.d("BoxCastPerf", "PERF: HomeViewModel.loadData launch block started")
            // Seed the WAS_INITIAL_REGION_MATCH if not set yet
            val systemCountry = java.util.Locale.getDefault().country.lowercase().let {
                if (it == "us" || it == "in" || it == "gb" || it == "uk" || it == "fr") it else "us"
            }
            userPrefs.wasInitialRegionMatchStream.first() ?: run {
                val currentReg = userPrefs.regionStream.first()
                val isMatch = (systemCountry == currentReg)
                userPrefs.setWasInitialRegionMatch(isMatch)
            }
            android.util.Log.d("BoxCastPerf", "PERF: Initial region checks completed")

            // --- BASE DATA FLOW (Restarts when Region or dismissal changes) ---
            userPrefs.regionStream
                .distinctUntilChanged()
                .collectLatest { region ->
                android.util.Log.d("BoxCastPerf", "PERF: regionStream emitted region=$region")
                if (cachedRegion != region) {
                    cachedRegion = region
                    cachedForYouTrending = emptyList()
                    cachedHeroItems = emptyList()
                    cachedTimeBlock = null
                    cachedLatestEpisodes = emptyList()
                    
                    _uiState.update { 
                        it.copy(
                            discoverPodcasts = emptyList(),
                            isFilterLoading = true
                        )
                    }
                }
                activeRegion = region
                
                val trendingState = MutableStateFlow<List<Podcast>>(emptyList())
                
                // 1. Fast Bootstrap Call (Briefing & Trending)
                val fastJob = launch {
                    _isTrendingLoaded.value = false
                    try {
                        android.util.Log.d("BoxCastPerf", "PERF: Fast Bootstrap API call starting for region=$region")
                        
                        val bootstrapData = podcastRepository.getHomeBootstrapDataFast(
                            country = region
                        )
                        
                        android.util.Log.d("BoxCastPerf", "PERF: Fast Bootstrap API call returned data successfully")
                        _briefingState.value = bootstrapData.briefing
                        _briefingChaptersState.value = bootstrapData.briefingChapters
                        trendingState.value = bootstrapData.trending
                        
                    } catch (e: Exception) {
                        android.util.Log.e("BoxCastTiming", "VM: Fast Bootstrap API load failed", e)
                    } finally {
                        _isTrendingLoaded.value = true
                        android.util.Log.d("BoxCastPerf", "PERF: _isTrendingLoaded set to TRUE")
                    }
                }

                // 2. Independent Curated Vibes Call (Fast GET)
                launch {
                    try {
                        val blockConfig = getTimeBlockConfig()
                        val vibeIds = blockConfig.genres.map { it.id }
                        val curatedVibesMap = podcastRepository.getCuratedVibes(vibeIds, region)
                        
                        val daySeed = java.time.LocalDate.now().toEpochDay()
                        val resolvedSections = blockConfig.genres.map { genre ->
                            val list = curatedVibesMap[genre.id] ?: emptyList()
                            val filtered = list
                                .filter { it.latestEpisode != null }
                                .shuffled(kotlin.random.Random(daySeed.toInt() + genre.title.hashCode()))
                                .take(10)
                            if (filtered.isNotEmpty()) {
                                CuratedSectionData(genre.title, genre.id, filtered)
                            } else null
                        }.filterNotNull()
                        
                        val block = if (resolvedSections.isNotEmpty()) {
                            CuratedTimeBlock(blockConfig.title, blockConfig.subtitle, blockConfig.icon, resolvedSections)
                        } else null
                        
                        _timeBlockState.value = block
                        cachedTimeBlock = block
                        
                        try {
                            val json = Json { ignoreUnknownKeys = true }
                            val prefs = getApplication<Application>().getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
                            val serializedVibes = json.encodeToString(curatedVibesMap)
                            prefs.edit().putString("cached_curated_vibes", serializedVibes).apply()
                        } catch (ce: Exception) {
                            android.util.Log.e("HomeViewModel", "Failed to cache curated vibes", ce)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BoxCastTiming", "VM: Curated Vibes load failed", e)
                    } finally {
                        _isCuratedLoaded.value = true
                    }
                }

                // 3. Background Personalized Recommendations Call
                launch {
                    fastJob.join()
                    _isRecommendationsLoaded.value = false
                    try {
                        android.util.Log.d("BoxCastTiming", "VM: Background personalized Home screen load for region=$region")
                        
                        val prefs = getApplication<Application>().getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
                        val interests = prefs.getStringSet("user_genres", emptySet())?.toList() ?: emptyList()
                        
                        val historyDeferred = async { playbackRepository.getHistoryForRecommendations(15) }
                        val subscribedIdsDeferred = async { subscriptionRepository.subscribedPodcastIds.first().toList() }
                        val subscribedPodcastsDeferred = async { subscriptionRepository.subscribedPodcasts.first() }
                        
                        val history = historyDeferred.await()
                        val subscribedIds = subscribedIdsDeferred.await()
                        val subscribedPodcasts = subscribedPodcastsDeferred.await()
                        val subscribedGenres = subscribedPodcasts.mapNotNull { it.genre }.distinct()
                        
                        val bootstrapData = podcastRepository.getHomeBootstrapData(
                            country = region,
                            vibeIds = emptyList(),
                            history = history,
                            interests = interests,
                            subscribedPodcastIds = subscribedIds,
                            subscribedGenres = subscribedGenres
                        )
                        
                        val distinctRecs = bootstrapData.recommendations
                            .distinctBy { it.id }
                            .distinctBy { it.title.lowercase().trim() }
                        _recommendations.value = distinctRecs
                        _isRecommendationsFallback.value = bootstrapData.isRecommendationsFallback
                        try {
                            val json = Json { ignoreUnknownKeys = true }
                            val serialized = json.encodeToString(distinctRecs)
                            prefs.edit()
                                .putString("cached_recommendations", serialized)
                                .putBoolean("is_recommendations_fallback", bootstrapData.isRecommendationsFallback)
                                .apply()
                        } catch (ce: Exception) {
                            android.util.Log.e("HomeViewModel", "Failed to cache recommendations", ce)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BoxCastTiming", "VM: Recommendations load failed", e)
                    } finally {
                        _isRecommendationsLoaded.value = true
                    }
                }
                
                combine(
                    trendingState, // Hot StateFlow — never completes
                    playbackRepository.resumeSessions,
                    subscriptionRepository.subscribedPodcasts,
                    playbackRepository.getAllHistory(),
                    _resolvedSerialEpisodes,
                    _recommendations,
                    playbackRepository.completedEpisodeIds,
                    _timeBlockState, // Re-emit when curated vibes resolve
                    _isTrendingLoaded,
                    _isCuratedLoaded,
                    _isRecommendationsLoaded,
                    userPrefs.hasDismissedHomeImportBannerStream,
                    _briefingState,
                    _briefingDismissedDate,
                    _briefingChaptersState,
                    _briefingDismissedForever,
                    userPrefs.hasDismissedRegionNudgeStream,
                    _seemsToLikePodcast,
                    _becauseYouLikeRecommendations,
                    _becauseYouLikePodcasts,
                    _isBecauseYouLikeLoading,
                    _isRecommendationsFallback
                ) { array ->
                    val dismissedDate = array[13] as String
                    val dismissedForever = array[15] as Boolean
                    HomeDataWrapper(
                        trending = array[0] as List<Podcast>,
                        resume = array[1] as List<cx.aswin.boxcast.core.data.PlaybackSession>,
                        subs = array[2] as List<Podcast>,
                        history = array[3] as List<cx.aswin.boxcast.core.data.database.ListeningHistoryEntity>,
                        resolvedSerial = array[4] as Map<String, Episode>,
                        recommendations = array[5] as List<Episode>,
                        completedEpisodeIds = array[6] as Set<String>,
                        isTrendingLoaded = array[8] as Boolean,
                        isCuratedLoaded = array[9] as Boolean,
                        isRecommendationsLoaded = array[10] as Boolean,
                        hasDismissedImportBanner = array[11] as Boolean,
                        briefing = array[12] as Briefing?,
                        briefingChapters = array[14] as List<cx.aswin.boxcast.core.model.Chapter>,
                        briefingDismissedDate = dismissedDate,
                        briefingDismissedForever = dismissedForever,
                        hasDismissedRegionNudge = array[16] as Boolean,
                        seemsToLikePodcast = array[17] as Podcast?,
                        becauseYouLikeRecommendations = array[18] as List<Episode>,
                        becauseYouLikePodcasts = array[19] as List<Podcast>,
                        isBecauseYouLikeLoading = array[20] as Boolean,
                        isRecommendationsFallback = array[21] as Boolean
                    )
                }.debounce(100L).collect { wrapper ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        val trendingList = wrapper.trending
                        val resumeList = wrapper.resume
                        val subs = wrapper.subs
                        val allHistory = wrapper.history
                        val resolvedSerial = wrapper.resolvedSerial
                        val completedEpisodeIds = wrapper.completedEpisodeIds
                    
                    // Compute completed count for review prompt logic
                    val completedCount = allHistory.count { it.isCompleted }
                    
                    // Check if review prompt should be shown (milestone-based)
                    if (!_showReviewPrompt.value && !_showFeedback.value && !_showPostReview.value) {
                        val shouldPrompt = userPrefs.shouldShowReviewPrompt(completedCount, playerState.value.isPlaying)
                        if (shouldPrompt) {
                            _showReviewPrompt.value = true
                        }
                    }
                    
                    // ... (Logic copied below) ...
                    
                    // Note: Resume/Hero logic shouldn't disappear when filtering genres...
                    // ...
                    
                        if (trendingList.isEmpty()) {
                             // Still loading
                        }

                        // Proceed to build UI even with partial trending list
                        val heroList = mutableListOf<SmartHeroItem>()
                        val usedPodcastIds = mutableSetOf<String>()

                        // A. Resume Sessions — Progressive Density
                        // 1 session  → 1 full card
                        // 2 sessions → 2 full cards
                        // 3-5 sessions → 1 full card + 1 grid card (2-4 items)
                        if (resumeList.isNotEmpty()) {
                            // Helper to convert a PlaybackSession to a Podcast
                             fun sessionToPodcast(session: cx.aswin.boxcast.core.data.PlaybackSession): Podcast {
                                 val epImage = session.imageUrl
                                 val podImage = session.podcastImageUrl
                                 val ratio = if (session.durationMs > 0) {
                                     (session.positionMs.toFloat() / session.durationMs.toFloat()).coerceIn(0f, 1f)
                                 } else 0f
                                 val finalPodId = session.podcastId.takeIf { it.isNotBlank() && it != "0" } ?: ""
                                 val parentPod = subs.find { it.id == finalPodId }
                                 val parentTitle = parentPod?.title.orEmpty()
                                 val finalPodTitle = session.podcastTitle
                                     .takeIf { it.isNotBlank() && it != "Unknown Podcast" }
                                     ?: parentTitle.ifBlank { "Podcast" }
                                 return Podcast(
                                     id = finalPodId,
                                     title = finalPodTitle,
                                     artist = "",
                                    imageUrl = if (!epImage.isNullOrEmpty()) epImage else podImage ?: "",
                                    fallbackImageUrl = podImage,
                                    description = "",
                                    genre = "Podcast",
                                    resumeProgress = ratio,
                                    latestEpisode = Episode(
                                        id = session.episodeId,
                                        title = session.episodeTitle,
                                        description = "",
                                        imageUrl = epImage ?: "",
                                        audioUrl = session.audioUrl ?: "",
                                        duration = (session.durationMs / 1000).toInt(),
                                        publishedDate = 0L,
                                        podcastTitle = finalPodTitle,
                                        podcastId = finalPodId,
                                        enclosureType = session.enclosureType
                                    )
                                )
                            }

                            val first = resumeList[0]
                            try {
                                val firstPodcast = sessionToPodcast(first)
                                val timeLeft = ((first.durationMs - first.positionMs) / 60000).coerceAtLeast(1)
                                heroList.add(
                                    SmartHeroItem(
                                        type = HeroType.RESUME,
                                        podcast = firstPodcast,
                                        label = "RESUME • ${timeLeft}m left",
                                        description = first.episodeTitle
                                    )
                                )
                                usedPodcastIds.add(firstPodcast.id)
                            } catch (e: Exception) { e.printStackTrace() }

                            if (resumeList.size == 2) {
                                // 2 sessions → second also gets a full card
                                val second = resumeList[1]
                                try {
                                    val secondPodcast = sessionToPodcast(second)
                                    val timeLeft = ((second.durationMs - second.positionMs) / 60000).coerceAtLeast(1)
                                    heroList.add(
                                        SmartHeroItem(
                                            type = HeroType.RESUME,
                                            podcast = secondPodcast,
                                            label = "RESUME • ${timeLeft}m left",
                                            description = second.episodeTitle
                                        )
                                    )
                                    usedPodcastIds.add(secondPodcast.id)
                                } catch (e: Exception) {}
                            } else if (resumeList.size > 2) {
                                // 3-5 sessions → remaining go into a grid card (max 4 items)
                                val gridCandidates = resumeList.drop(1).take(4)
                                val gridPodcasts = gridCandidates.mapNotNull { session ->
                                    try {
                                        val pod = sessionToPodcast(session)
                                        usedPodcastIds.add(pod.id)
                                        pod
                                    } catch (e: Exception) { null }
                                }
                                if (gridPodcasts.isNotEmpty()) {
                                    heroList.add(
                                        SmartHeroItem(
                                            type = HeroType.RESUME_GRID,
                                            podcast = gridPodcasts.first(),
                                            label = "JUMP BACK IN",
                                            description = null,
                                            gridItems = gridPodcasts
                                        )
                                    )
                                }
                            }
                        }

                        // C. Smart "Catch Up" with Three-Tier Priority
                        // Bucket 1: Unplayed (top priority, hero-eligible)
                        // Bucket 2: In Progress (sorted by most recently played)
                        // Bucket 3: Completed (sorted by recency, no time limit)
                        val unplayedBucket = mutableListOf<Podcast>()
                        val inProgressBucket = mutableListOf<Pair<Podcast, Long>>() // Pair(podcast, lastPlayedAt)
                        val completedBucket = mutableListOf<Pair<Podcast, Long>>()
                        
                        if (subs.isNotEmpty()) {
                            try {
                                val candidates = subs
                                
                                if (candidates.isNotEmpty()) {
                                    for (pod in candidates) {
                                        val sort = pod.preferredSort ?: "newest"
                                        val freshEpisode = if (sort == "oldest") {
                                            resolvedSerial[pod.id] ?: pod.latestEpisode
                                        } else {
                                            pod.latestEpisode
                                        } ?: continue
                                        val freshEpisodeWithContext = freshEpisode.copy(
                                            podcastTitle = pod.title,
                                            podcastId = pod.id
                                        )
                                        val history = allHistory.find { it.episodeId == freshEpisodeWithContext.id }
                                        
                                        when {
                                            // Never touched
                                            history == null || (history.progressMs == 0L && !history.isCompleted) -> {
                                                unplayedBucket.add(pod.copy(
                                                    latestEpisode = freshEpisodeWithContext,
                                                    episodeStatus = EpisodeStatus.UNPLAYED
                                                ))
                                            }
                                            // Started but not finished
                                            !history.isCompleted && history.progressMs > 0L -> {
                                                val progress = if (history.durationMs > 0)
                                                    (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
                                                else 0f
                                                inProgressBucket.add(
                                                    pod.copy(
                                                        latestEpisode = freshEpisodeWithContext,
                                                        resumeProgress = progress,
                                                        episodeStatus = EpisodeStatus.IN_PROGRESS
                                                    ) to history.lastPlayedAt
                                                )
                                            }
                                            // Completed — include all, no time restriction
                                            history.isCompleted -> {
                                                completedBucket.add(
                                                    pod.copy(
                                                        latestEpisode = freshEpisodeWithContext,
                                                        resumeProgress = 1f,
                                                        episodeStatus = EpisodeStatus.COMPLETED
                                                    ) to history.lastPlayedAt
                                                )
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }


                        // 1. Group/Index listening history for O(N + M) efficiency
                        val historyByEpisode = allHistory.associateBy { it.episodeId }
                        val subsMap = subs.associateBy { it.id }

                        // Calculate score map for all subscribed podcasts using the shared utility (only on first calculation)
                        val podScoresMap = if (stablePodcastOrder == null || stableMixtapePodcasts == null) {
                            PodcastScoring.calculateScores(
                                podcasts = subs.map { it.toScorable() },
                                allHistory = allHistory
                            )
                        } else {
                            emptyMap()
                        }

                        // Hybrid Session-Stable sorting order logic
                        val currentSubIds = subs.map { it.id }.toSet()
                        val previousOrder = stablePodcastOrder

                        val orderToUse = if (previousOrder == null) {
                            // First calculation: calculate scores and sort
                            val sortedList = subs.map { pod ->
                                pod to (podScoresMap[pod.id] ?: 0.0)
                            }.sortedWith(
                                compareByDescending<Pair<Podcast, Double>> { it.second }
                                    .thenBy { it.first.title }
                            ).map { it.first.id }
                            stablePodcastOrder = sortedList
                            sortedList
                        } else {
                            // Check if a show was unsubscribed/removed
                            val existingOrder = previousOrder.filter { it in currentSubIds }
                            
                            // Check if a new show was subscribed (added)
                            val newSubscribedIds = currentSubIds.filter { it !in existingOrder.toSet() }
                            
                            if (newSubscribedIds.isNotEmpty()) {
                                // Prepend new subscriptions to the start of the list in memory
                                val updatedOrder = newSubscribedIds + existingOrder
                                stablePodcastOrder = updatedOrder
                                updatedOrder
                            } else {
                                stablePodcastOrder = existingOrder
                                existingOrder
                            }
                        }

                        val sortedSubs = orderToUse.mapNotNull { subsMap[it] }

                        val cachedMix = stableMixtapePodcasts
                        val cachedCount = stableMixtapeCount
                        val cachedUnplayed = stableCurrentUnplayedEpisodes

                        val mixtapePodcasts: List<Podcast>
                        val mixtapeCount: Int

                        if (cachedMix != null && cachedCount != null && cachedUnplayed != null) {
mixtapePodcasts = cachedMix
                            mixtapeCount = cachedCount
                            currentUnplayedEpisodes = cachedUnplayed
                        } else {
                            // 2. Mixtape Episodes Calculation & Scoring
                            val nowMs = System.currentTimeMillis()

                            // A. In-Progress Episodes (Deduplicated per podcast: take the most recently played)
                            val inProgressCandidates = allHistory.filter { history ->
                                if (history.isCompleted || history.progressMs <= 0L) return@filter false
                                
                                // 2. 30-day stale session decay rule
                                if (nowMs - history.lastPlayedAt > 30L * 24L * 3600L * 1000L) return@filter false
                                
                                // 3. Intro/Outro rules for mixtape candidates
                                val ratio = if (history.durationMs > 0L) history.progressMs.toDouble() / history.durationMs.toDouble() else 0.0
                                val remainingMs = history.durationMs - history.progressMs
                                if (ratio < 0.10) return@filter false
                                if (ratio > 0.90 || (history.durationMs > 0L && remainingMs < 120_000L)) return@filter false
                                
                                true
                            }.groupBy { it.podcastId }
                             .mapValues { (_, eps) -> eps.maxByOrNull { it.lastPlayedAt } }
                             .values.filterNotNull()

                            val inProgressMixtapeCandidates = inProgressCandidates.mapNotNull { history ->
                                val pod = subsMap[history.podcastId] ?: Podcast(
                                    id = history.podcastId,
                                    title = history.podcastName,
                                    artist = "",
                                    imageUrl = history.podcastImageUrl ?: "",
                                    fallbackImageUrl = history.podcastImageUrl ?: "",
                                    subscribedAt = 0L
                                )
                                val ep = Episode(
                                    id = history.episodeId,
                                    title = history.episodeTitle,
                                    description = history.episodeDescription ?: "",
                                    audioUrl = history.episodeAudioUrl ?: "",
                                    imageUrl = history.episodeImageUrl,
                                    duration = (history.durationMs / 1000L).toInt(),
                                    podcastId = history.podcastId,
                                    podcastTitle = history.podcastName
                                )
                                val isCompleted = historyByEpisode[ep.id]?.isCompleted == true
                                if (isCompleted) null else pod to ep to history
                            }.map { (podAndEp, history) ->
                                val (pod, ep) = podAndEp
                                val parentPodScore = podScoresMap[history.podcastId] ?: 0.0
                                val progressRatio = if (history.durationMs > 0L) {
                                    history.progressMs.toDouble() / history.durationMs.toDouble()
                                } else 0.0
                                val score = 1000.0 + progressRatio * 500.0 + MIXTAPE_AFFINITY_WEIGHT * parentPodScore
                                MixtapeCandidate(
                                    podcast = pod,
                                    episode = ep,
                                    score = score,
                                    isProgress = true,
                                    progressMs = history.progressMs,
                                    durationMs = history.durationMs,
                                    episodeId = ep.id
                                )
                            }

                            // B. Unplayed New Drops (Episodes published after subscription date)
                            val unplayedDropsCandidates = subs.mapNotNull { pod ->
                                val latestEp = pod.latestEpisode
                                if (latestEp != null) {
                                    val history = historyByEpisode[latestEp.id]
                                    val isUnplayed = history == null || (history.progressMs == 0L && !history.isCompleted)
                                    if (isUnplayed) {
                                        pod to latestEp.copy(podcastTitle = pod.title, podcastId = pod.id)
                                    } else null
                                } else null
                            }

                            val unplayedDropsMixtapeCandidates = unplayedDropsCandidates.map { (pod, ep) ->
                                val isNewRelease = ep.publishedDate > (pod.subscribedAt / 1000L)
                                val freshnessBoost = if (isNewRelease) {
                                    val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0
                                    500.0 / (1.0 + hoursSinceRelease.coerceAtLeast(0.0) / 24.0)
                                } else 0.0
                                val newTagBoost = if (isNewRelease) 200.0 else 0.0
                                val serialBoost = if (pod.preferredSort == "oldest") 150.0 else 0.0
                                val parentPodScore = podScoresMap[pod.id] ?: 0.0
                                val score = 500.0 + freshnessBoost + newTagBoost + serialBoost + MIXTAPE_AFFINITY_WEIGHT * parentPodScore
                                MixtapeCandidate(
                                    podcast = pod,
                                    episode = ep,
                                    score = score,
                                    isProgress = false,
                                    progressMs = 0L,
                                    durationMs = 0L,
                                    episodeId = ep.id
                                )
                            }

                            // C. Merge and sort candidates
                            val allMixtapeCandidates = (inProgressMixtapeCandidates + unplayedDropsMixtapeCandidates)
                            val deduplicatedCandidates = mutableListOf<MixtapeCandidate>()
                            val seenEpisodeIds = mutableSetOf<String>()
                            for (cand in allMixtapeCandidates) {
                                if (seenEpisodeIds.add(cand.episodeId)) {
                                    deduplicatedCandidates.add(cand)
                                }
                            }

                            val inProgressList = deduplicatedCandidates.filter { it.isProgress }
                                .sortedByDescending { it.score }
                                .toMutableList()

                            val unplayedList = deduplicatedCandidates.filter { !it.isProgress }
                                .sortedByDescending { it.score }
                                .toMutableList()

                            val orderedCandidates = mutableListOf<MixtapeCandidate>()

                            for (ipCand in inProgressList) {
                                orderedCandidates.add(ipCand)
                                // Find the corresponding unplayed next episode for this podcast
                                val nextEpCand = unplayedList.find { it.podcast.id == ipCand.podcast.id }
                                if (nextEpCand != null) {
                                    orderedCandidates.add(nextEpCand)
                                    unplayedList.remove(nextEpCand)
                                }
                            }
                            // Add the remaining unplayed candidates
                            orderedCandidates.addAll(unplayedList)

                            val top10Candidates = orderedCandidates.take(MAX_MIXTAPE_ITEMS).toMutableList()

                            // D. Trending/Popular Episode Fallback for Empty States
                            if (top10Candidates.size < 3) {
                                val remaining = 3 - top10Candidates.size
                                val fallbackEpisodes = wrapper.recommendations.filter { ep ->
                                    val hist = historyByEpisode[ep.id]
                                    val isUnplayed = hist == null || (hist.progressMs == 0L && !hist.isCompleted)
                                    isUnplayed && top10Candidates.none { it.episodeId == ep.id }
                                }.take(remaining)
                                
                                fallbackEpisodes.forEach { ep ->
                                    val matchingPod = subsMap[ep.podcastId] ?: Podcast(
                                        id = ep.podcastId ?: "",
                                        title = ep.podcastTitle ?: "",
                                        artist = "",
                                        imageUrl = ep.imageUrl ?: "",
                                        fallbackImageUrl = ep.imageUrl ?: ""
                                    )
                                    top10Candidates.add(
                                        MixtapeCandidate(
                                            podcast = matchingPod,
                                            episode = ep,
                                            score = 100.0,
                                            isProgress = false,
                                            progressMs = 0L,
                                            durationMs = 0L,
                                            episodeId = ep.id
                                        )
                                    )
                                }
                            }

                            val finalCandidates = top10Candidates

                            // E. Map to Podcast objects
                            android.util.Log.d("HomeViewModelResolve", "Mixtape Final Candidates List: size=${finalCandidates.size}")
                            mixtapePodcasts = finalCandidates.mapIndexed { idx, cand ->
                                val ratio = if (cand.durationMs > 0) {
                                    (cand.progressMs.toFloat() / cand.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else if (cand.isProgress) {
                                    0.5f
                                } else {
                                    0.0f
                                }

                                val status = when {
                                    cand.isProgress -> EpisodeStatus.IN_PROGRESS
                                    historyByEpisode[cand.episodeId]?.isCompleted == true -> EpisodeStatus.COMPLETED
                                    else -> EpisodeStatus.UNPLAYED
                                }

                                android.util.Log.d("HomeViewModelResolve", "Mixtape Candidate[$idx]: pod=${cand.podcast.title} ep=${cand.episode.title} status=$status isProgress=${cand.isProgress} ratio=$ratio")

                                cand.podcast.copy(
                                    latestEpisode = cand.episode,
                                    resumeProgress = if (status == EpisodeStatus.IN_PROGRESS) ratio else if (status == EpisodeStatus.COMPLETED) 1f else null,
                                    episodeStatus = status
                                )
                            }

                            mixtapeCount = finalCandidates.count { cand ->
                                val isCompleted = historyByEpisode[cand.episodeId]?.isCompleted == true
                                !isCompleted
                            }

                            currentUnplayedEpisodes = finalCandidates.map { it.episode }

                            // Save to session cache
                            stableMixtapePodcasts = mixtapePodcasts
                            stableMixtapeCount = mixtapeCount
                            stableCurrentUnplayedEpisodes = currentUnplayedEpisodes
                        }
                        if (subs.size == 1 && _selectedPodcastId.value == null) {
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(500)
                                selectPodcast(subs.first().id)
                            }
                        }

                        // B. New Episodes — Same Progressive Density
                        // 1 unplayed → 1 full card
                        // 2 unplayed → 2 full cards
                        // 3-5 unplayed → 1 full card + 1 grid card (2-4 items)
                        if (unplayedBucket.isNotEmpty()) {
                            val first = unplayedBucket.first()
                            val firstSort = first.preferredSort ?: "newest"
                            heroList.add(
                                SmartHeroItem(
                                    type = HeroType.JUMP_BACK_IN,
                                    podcast = first,
                                    label = if (firstSort == "oldest") "NEXT" else if (unplayedBucket.size == 1) "NEW EPISODE" else "FRESH DROP",
                                    description = first.latestEpisode?.title
                                )
                            )
                            usedPodcastIds.add(first.id)

                            if (unplayedBucket.size == 2) {
                                // 2 unplayed → second also gets a full card
                                val second = unplayedBucket[1]
                                val secondSort = second.preferredSort ?: "newest"
                                heroList.add(
                                    SmartHeroItem(
                                        type = HeroType.JUMP_BACK_IN,
                                        podcast = second,
                                        label = if (secondSort == "oldest") "NEXT" else "NEW EPISODE",
                                        description = second.latestEpisode?.title
                                    )
                                )
                                usedPodcastIds.add(second.id)
                            } else if (unplayedBucket.size > 2) {
                                // 3-5 unplayed → remaining go into a grid card (max 4 items)
                                val gridDrops = unplayedBucket.drop(1).take(4)
                                heroList.add(
                                    SmartHeroItem(
                                        type = HeroType.NEW_EPISODES_GRID,
                                        podcast = gridDrops.first(),
                                        label = "NEW EPISODES",
                                        description = null,
                                        gridItems = gridDrops
                                    )
                                )
                                usedPodcastIds.addAll(gridDrops.map { it.id })
                            }
                        }

                        // C. Spotlight (Fill to 8)
                        var i = 0
                        var spotlightAddedCount = 0
                        while (heroList.size < 8 && i < trendingList.size) {
                            val pod = trendingList[i]
                            if (!usedPodcastIds.contains(pod.id)) {
                                val label = when {
                                    spotlightAddedCount == 0 -> when (region.lowercase()) {
                                        "in" -> "#1 IN INDIA"
                                        "gb", "uk" -> "#1 IN UK"
                                        "fr" -> "#1 IN FRANCE"
                                        else -> "#1 IN US"
                                    }
                                    pod.genre.isNotEmpty() && !pod.genre.equals("Podcast", ignoreCase = true) -> "TRENDING IN ${pod.genre.uppercase()}"
                                    else -> "TRENDING"
                                }
                                val spotlightDesc = pod.latestEpisode?.title ?: pod.genre
                                
                                val latestEp = pod.latestEpisode
                                val epUrl = latestEp?.imageUrl
                                
                                val displayPodcast = if (!epUrl.isNullOrEmpty()) {
                                    pod.copy(
                                        imageUrl = epUrl,
                                        fallbackImageUrl = pod.imageUrl 
                                    )
                                } else {
                                    pod.copy(fallbackImageUrl = pod.imageUrl)
                                }
                                
                                heroList.add(
                                    SmartHeroItem(
                                        type = HeroType.SPOTLIGHT,
                                        podcast = displayPodcast,
                                        label = label,
                                        description = spotlightDesc
                                    )
                                )
                                usedPodcastIds.add(pod.id)
                                spotlightAddedCount++
                            }
                            i++
                        }


                        // --- Unified Time Block (non-blocking: use current state or null) ---
                        val timeBlock = _timeBlockState.value
                        
                        // Update used IDs
                        timeBlock?.sections?.forEach { sec -> 
                            sec.podcasts.forEach { usedPodcastIds.add(it.id) } 
                        }

                        val remaining = trendingList.filter { !usedPodcastIds.contains(it.id) }
                        val discover = remaining 

                        if (trendingList.isNotEmpty()) {
                            cachedRegion = region
                            cachedForYouTrending = trendingList
                            cachedHeroItems = heroList
                            cachedLatestEpisodes = mixtapePodcasts
                        }

                        val episodePlaybackState = allHistory.associate { history ->
                            val ratio = if (history.durationMs > 0) {
                                (history.progressMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val status = when {
                                history.isCompleted -> EpisodeStatus.COMPLETED
                                history.progressMs > 0L -> EpisodeStatus.IN_PROGRESS
                                else -> EpisodeStatus.UNPLAYED
                            }
                            history.episodeId to (status to ratio)
                        }.toMutableMap()

                        // Enrich map with manually/bulk completed episode IDs to ensure their checkmarks render
                        completedEpisodeIds.forEach { completedId ->
                            if (!episodePlaybackState.containsKey(completedId)) {
                                episodePlaybackState[completedId] = (EpisodeStatus.COMPLETED to 1f)
                            }
                        }

                        val shouldShowNudge = !wrapper.hasDismissedRegionNudge && (systemCountry != region)

                        val initialLoading = !wrapper.isTrendingLoaded

                        // Daily Briefing visibility filter logic
                        val rawBriefing = wrapper.briefing
                        val showBriefing = if (rawBriefing != null) {
                            val briefingEpisodeId = "briefing_${rawBriefing.region}_${rawBriefing.date}"
                            val isCompleted = completedEpisodeIds.contains(briefingEpisodeId)
                            val isDismissed = rawBriefing.date == wrapper.briefingDismissedDate
                            val isDismissedForever = wrapper.briefingDismissedForever
                            val isDisplayedInResume = heroList.any { item ->
                                when (item.type) {
                                    HeroType.RESUME -> {
                                        item.podcast.id == "briefing_${rawBriefing.region}"
                                    }
                                    HeroType.RESUME_GRID -> {
                                        item.gridItems.any { it.id == "briefing_${rawBriefing.region}" }
                                    }
                                    else -> false
                                }
                            }
                            !isCompleted && !isDismissed && !isDismissedForever && !isDisplayedInResume
                        } else {
                            false
                        }

                        _uiState.value = HomeUiState(
                            heroItems = heroList,
                            latestEpisodes = mixtapePodcasts,
                            unplayedEpisodeCount = mixtapeCount,
                            completedEpisodeCount = completedCount,
                            subscribedPodcasts = sortedSubs,
                            selectedCategory = _selectedCategory.value,
                            timeBlock = timeBlock,
                            discoverPodcasts = discover,
                            recommendations = wrapper.recommendations,
                            isLoading = initialLoading,
                            isFilterLoading = trendingList.isEmpty(),
                            isError = false,
                            showRegionNudge = shouldShowNudge,
                            systemRegionCode = systemCountry,
                            activeRegionCode = region,
                            selectedPodcastId = _selectedPodcastId.value,
                            selectedPodcastEpisodes = _selectedPodcastEpisodes.value,
                            isSelectedPodcastLoading = _isSelectedPodcastLoading.value,
                            episodePlaybackState = episodePlaybackState,
                            showImportBanner = sortedSubs.isEmpty() && !wrapper.hasDismissedImportBanner,
                            briefing = if (showBriefing) rawBriefing else null,
                            briefingChapters = if (showBriefing) wrapper.briefingChapters else emptyList(),
                            isRecommendationsLoading = !wrapper.isRecommendationsLoaded,
                            isCuratedLoading = !wrapper.isCuratedLoaded,
                            seemsToLikePodcast = wrapper.seemsToLikePodcast,
                            becauseYouLikeRecommendations = wrapper.becauseYouLikeRecommendations,
                            becauseYouLikePodcasts = wrapper.becauseYouLikePodcasts,
                            isBecauseYouLikeLoading = wrapper.isBecauseYouLikeLoading,
                            isRecommendationsFallback = wrapper.isRecommendationsFallback
                        )
                    }
                }
            }
        }
        
        // --- CATEGORY OBSERVER (Considers Region) ---
        viewModelScope.launch {
            combine(_selectedCategory, userPrefs.regionStream) { category, region -> 
                category to region 
            }.collectLatest { (category, region) ->
                if (category == null) {
                    // "For You" - use cached data instantly if it matches current region
                    if (cachedRegion == region && cachedHeroItems.isNotEmpty()) {
                        val discover = cachedForYouTrending.filter { pod ->
                            !cachedHeroItems.any { it.podcast.id == pod.id } &&
                            !(cachedTimeBlock?.sections?.any { sec -> sec.podcasts.any { it.id == pod.id } } ?: false)
                        }
                        
                        _uiState.update { 
                            it.copy(
                                selectedCategory = null,
                                discoverPodcasts = discover,
                                isFilterLoading = false
                            )
                        }
                    } else {
                        // Region has changed or cache is empty / stale, so clear discover list and wait for load
                        _uiState.update {
                            it.copy(
                                selectedCategory = null,
                                discoverPodcasts = emptyList(),
                                isFilterLoading = true
                            )
                        }
                    }
                } else {
                    // Category selected
                    _uiState.update { it.copy(
                        isFilterLoading = true, 
                        selectedCategory = category,
                        discoverPodcasts = emptyList()
                    ) }
                    
                    try {
                        android.util.Log.d("HomeViewModel", "Category: Fetching '$category' for region '$region'...")
                        var finalList: List<Podcast> = emptyList()
                        podcastRepository.getTrendingPodcastsStream(region, 50, category.lowercase())
                            .collect { items ->
                                finalList = items
                                _uiState.update { 
                                    it.copy(
                                        discoverPodcasts = items,
                                        isFilterLoading = items.size < 10
                                    )
                                }
                            }
                        _uiState.update { 
                            it.copy(
                                discoverPodcasts = finalList,
                                isFilterLoading = false
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HomeViewModel", "Category stream error", e)
                        _uiState.update { it.copy(isFilterLoading = false) }
                    }
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
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackDiscoverCategoryFiltered(category ?: "All")
    }
    
    fun toggleSubscription(podcastId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val podcast = state.timeBlock?.sections?.flatMap { it.podcasts }?.find { it.id == podcastId }
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
    
    // --- Helper Logic ---
    data class TimeBlockConfig(val title: String, val subtitle: String, val icon: ImageVector, val genres: List<GenreConfig>)
    data class GenreConfig(val id: String, val title: String)

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

    private fun getTimeBlockConfig(): TimeBlockConfig {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val day = cal.get(Calendar.DAY_OF_WEEK) // Sun=1...
        
        val isWeekend = day == Calendar.SATURDAY || day == Calendar.SUNDAY
        val isFriday = day == Calendar.FRIDAY

        return when (hour) {
            in 5..11 -> TimeBlockConfig(
                title = "Good Morning",
                subtitle = if(isWeekend) "Catch up on the week." else "Start your day with these updates.",
                icon = Icons.Rounded.WbSunny,
                genres = listOf(
                    GenreConfig("morning_news", "Global Headlines & Politics"),
                    GenreConfig("morning_motivation", "Ideas, Culture & Reflections"), 
                    GenreConfig("business_insider", "Markets, Tech & Business")
                )
            )
            in 12..16 -> TimeBlockConfig(
                title = "Afternoon Break",
                subtitle = "Smart conversations to keep you going.",
                icon = Icons.Rounded.WbSunny,
                genres = listOf(
                    GenreConfig("science_explainer", "Science, Cosmos & Exploration"),
                    GenreConfig("tech_culture", "Digital Culture & Emerging Tech"),
                    GenreConfig("creative_focus", "Design, Art & Creative Practice")
                )
            )
            in 17..22 -> TimeBlockConfig(
                title = "Evening Unwind",
                subtitle = if(isFriday) "Kick off the weekend." else "Relax, laugh, and catch up.",
                icon = Icons.Rounded.WbTwilight,
                genres = listOf(
                    GenreConfig("comedy_gold", "Satire, Comedy & Conversation"),
                    GenreConfig("tv_film_buff", "Pop Culture, Film & Television"),
                    GenreConfig("sports_fan", "Sports Culture & Field Analysis")
                )
            )
            else -> TimeBlockConfig(
                title = "Late Night Listen",
                subtitle = "Stories for the dark hours.",
                icon = Icons.Rounded.NightsStay,
                genres = listOf(
                    GenreConfig("true_crime_sleep", "True Crime & Investigative Files"),
                    GenreConfig("history_buff", "Historical Narratives & Chronicles"),
                    GenreConfig("mystery_thriller", "Suspense, Thrillers & Drama")
                )
            )
        }
    }
    
    fun dismissRegionNudge() {
        viewModelScope.launch {
            userPrefs.dismissRegionNudge()
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

    
    private suspend fun resolveFavoritePodcast(
        overriddenId: String?,
        subscriptions: List<Podcast>,
        historyList: List<ListeningHistoryEntity>
    ): Podcast? {
        if (overriddenId != null) {
            val sub = subscriptions.find { it.id == overriddenId }
            if (sub != null) return sub
            
            val localEntity = database.podcastDao().getPodcast(overriddenId)
            if (localEntity != null) {
                return Podcast(
                    id = localEntity.podcastId,
                    title = localEntity.title,
                    artist = localEntity.author ?: "",
                    imageUrl = localEntity.imageUrl ?: "",
                    fallbackImageUrl = localEntity.latestEpisode?.imageUrl ?: "",
                    description = localEntity.description,
                    genre = localEntity.genre ?: "Podcast"
                )
            }
            
            val hist = historyList.find { it.podcastId == overriddenId }
            if (hist != null) {
                return Podcast(
                    id = hist.podcastId,
                    title = hist.podcastName,
                    artist = "",
                    imageUrl = hist.podcastImageUrl ?: "",
                    fallbackImageUrl = "",
                    description = ""
                )
            }
            
            return null
        }

        if (subscriptions.isEmpty() && historyList.isEmpty()) return null

        val lastPlayedMap = mutableMapOf<String, Long>()
        val podcastNameMap = mutableMapOf<String, String>()
        val podcastImageMap = mutableMapOf<String, String>()

        val scores = calculatePodcastAffinityScores(
            subscriptions = subscriptions,
            historyList = historyList,
            lastPlayedMap = lastPlayedMap,
            podcastNameMap = podcastNameMap,
            podcastImageMap = podcastImageMap
        )

        if (scores.isEmpty()) return null

        val topEntry = scores.maxByOrNull { entry ->
            entry.value.toLong() * 1_000_000_000_000L + lastPlayedMap.getOrDefault(entry.key, 0L)
        } ?: return null

        val topPodId = topEntry.key
        val topScore = topEntry.value

        if (topScore < 15) return null

        val sub = subscriptions.find { it.id == topPodId }
        if (sub != null) return sub

        val localEntity = database.podcastDao().getPodcast(topPodId)
        if (localEntity != null) {
            return Podcast(
                id = localEntity.podcastId,
                title = localEntity.title,
                artist = localEntity.author ?: "",
                imageUrl = localEntity.imageUrl ?: "",
                fallbackImageUrl = localEntity.latestEpisode?.imageUrl ?: "",
                description = localEntity.description,
                genre = localEntity.genre ?: "Podcast"
            )
        }

        return Podcast(
            id = topPodId,
            title = podcastNameMap[topPodId] ?: "Podcast",
            artist = "",
            imageUrl = podcastImageMap[topPodId] ?: "",
            fallbackImageUrl = "",
            description = ""
        )
    }

    private fun getHistoryScoreIncrement(history: ListeningHistoryEntity): Int {
        var score = 0
        if (history.isCompleted) {
            score += 20
        } else {
            if (history.progressMs >= 300_000L) {
                score += 15
            } else if (history.progressMs >= 60_000L) {
                score += 5
            }
        }
        if (history.isLiked) {
            score += 40
        }
        return score
    }

    private fun calculatePodcastAffinityScores(
        subscriptions: List<Podcast>,
        historyList: List<ListeningHistoryEntity>,
        lastPlayedMap: MutableMap<String, Long>,
        podcastNameMap: MutableMap<String, String>,
        podcastImageMap: MutableMap<String, String>
    ): Map<String, Int> {
        val scores = mutableMapOf<String, Int>()

        historyList.forEach { history ->
            val podId = history.podcastId
            if (podId.isNotEmpty()) {
                podcastNameMap[podId] = history.podcastName
                podcastImageMap[podId] = history.podcastImageUrl ?: ""
                
                val currentLastPlayed = lastPlayedMap.getOrDefault(podId, 0L)
                if (history.lastPlayedAt > currentLastPlayed) {
                    lastPlayedMap[podId] = history.lastPlayedAt
                }

                val score = scores.getOrDefault(podId, 0) + getHistoryScoreIncrement(history)
                scores[podId] = score
            }
        }

        subscriptions.forEach { sub ->
            val score = scores.getOrDefault(sub.id, 0) + 100
            scores[sub.id] = score
            podcastNameMap[sub.id] = sub.title
            podcastImageMap[sub.id] = sub.imageUrl
        }
        
        return scores
    }

    private fun fetchBecauseYouLikeRecommendations(podcast: Podcast, region: String) {
        viewModelScope.launch {
            _isBecauseYouLikeLoading.value = true
            try {
                val title = podcast.title
                val desc = podcast.description ?: ""
                val id = podcast.id
                
                android.util.Log.d("HomeViewModel", "Fetching because-you-like recommendations for: $title (ID: $id), region: $region")
                val data = podcastRepository.getBecauseYouLikeRecommendations(
                    podcastTitle = title,
                    podcastDescription = desc,
                    excludePodcastId = id,
                    country = region
                )
                
                val distinctPodcasts = data.podcasts
                    .distinctBy { it.id }
                    .distinctBy { it.title.lowercase().trim() }
                val distinctEpisodes = data.episodes
                    .distinctBy { it.id }
                    .distinctBy { it.title.lowercase().trim() }
                
                android.util.Log.d("HomeViewModel", "Fetched because-you-like: podcasts count = ${distinctPodcasts.size}, episodes count = ${distinctEpisodes.size}")
                
                _becauseYouLikePodcasts.value = distinctPodcasts
                _becauseYouLikeRecommendations.value = distinctEpisodes
                
                try {
                    val prefs = getApplication<Application>().getSharedPreferences("boxcast_prefs", Context.MODE_PRIVATE)
                    val json = Json { ignoreUnknownKeys = true }
                    val serializedEpisodes = json.encodeToString(distinctEpisodes)
                    val serializedPodcasts = json.encodeToString(distinctPodcasts)
                    prefs.edit()
                        .putString("cached_byl_recommendations", serializedEpisodes)
                        .putString("cached_byl_podcasts", serializedPodcasts)
                        .putString("cached_byl_podcast_id", id)
                        .apply()
                } catch (ce: Exception) {
                    android.util.Log.e("HomeViewModel", "Failed to cache because-you-like recommendations", ce)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Failed to fetch because-you-like recommendations", e)
            } finally {
                _isBecauseYouLikeLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
