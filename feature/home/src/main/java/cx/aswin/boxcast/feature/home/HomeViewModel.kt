package cx.aswin.boxcast.feature.home

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxcast.core.data.PodcastRepository
import cx.aswin.boxcast.core.data.SubscriptionRepository
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.model.EpisodeStatus
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitAll
import java.util.Calendar
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Bedtime

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
    val isLoading: Boolean = false, // Initial full-screen loader
    val isFilterLoading: Boolean = false, // Inline loader when switching genres
    val isError: Boolean = false
)

data class HomeDataWrapper(
    val trending: List<Podcast>,
    val resume: List<cx.aswin.boxcast.core.data.PlaybackSession>,
    val subs: List<Podcast>,
    val history: List<cx.aswin.boxcast.core.data.database.ListeningHistoryEntity>
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

class HomeViewModel(
    application: Application,
    apiBaseUrl: String,
    publicKey: String,
    private val playbackRepository: cx.aswin.boxcast.core.data.PlaybackRepository
) : AndroidViewModel(application) {
    
    val podcastRepository = PodcastRepository(baseUrl = apiBaseUrl, publicKey = publicKey, context = application)
    private val database = cx.aswin.boxcast.core.data.database.BoxCastDatabase.getDatabase(application)
    private val subscriptionRepository = cx.aswin.boxcast.core.data.SubscriptionRepository(database.podcastDao(), cx.aswin.boxcast.core.data.analytics.AnalyticsHelper(application, cx.aswin.boxcast.core.data.privacy.ConsentManager(application))) // This might be complex to reconstruct.
    // Actually, SubscriptionRepository takes dao and analytcs.
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
    
    private val userPrefs = cx.aswin.boxcast.core.data.UserPreferencesRepository(application)
    
    // Expose region to UI
    val currentRegion = userPrefs.regionStream
    
    // Playback state to UI
    val playerState = playbackRepository.playerState
    
    // Cached base data (For You)
    private var cachedForYouTrending: List<Podcast> = emptyList()
    private var cachedHeroItems: List<SmartHeroItem> = emptyList()
    private var cachedTimeBlock: CuratedTimeBlock? = null
    private var cachedLatestEpisodes: List<Podcast> = emptyList()
    
    // Store current region for use in other scopes
    private var activeRegion = "us"

    init {
        loadData()
        startBackgroundSync()
    }



    private fun loadData() {
        viewModelScope.launch {
            // --- BASE DATA FLOW (Restarts when Region changes) ---
            userPrefs.regionStream.collectLatest { region ->
                activeRegion = region
                
                combine(
                    podcastRepository.getTrendingPodcastsStream(region, 50, null) // Dynamic Region
                        .onStart { 
                            android.util.Log.d("BoxCastTiming", "VM: Base stream starting for region=$region")
                            emit(emptyList()) 
                        },
                    playbackRepository.resumeSessions
                        .onStart { emit(emptyList()) },
                    subscriptionRepository.subscribedPodcasts
                        .onStart { emit(emptyList()) },
                    playbackRepository.getAllHistory()
                        .onStart { emit(emptyList()) }
                ) { trendingList, resumeList, subs, allHistory ->
                     HomeDataWrapper(trendingList, resumeList, subs, allHistory)
                }.collect { wrapper ->
                    val trendingList = wrapper.trending
                    val resumeList = wrapper.resume
                    val subs = wrapper.subs
                    val allHistory = wrapper.history
                    
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
                                return Podcast(
                                    id = session.podcastId,
                                    title = session.podcastTitle,
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
                                        publishedDate = 0L
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
                                        val freshEpisode = pod.latestEpisode ?: continue
                                        val history = allHistory.find { it.episodeId == freshEpisode.id }
                                        
                                        when {
                                            // Never touched
                                            history == null || (history.progressMs == 0L && !history.isCompleted) -> {
                                                unplayedBucket.add(pod.copy(
                                                    latestEpisode = freshEpisode,
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
                                                        latestEpisode = freshEpisode,
                                                        resumeProgress = progress,
                                                        episodeStatus = EpisodeStatus.IN_PROGRESS
                                                    ) to history.lastPlayedAt
                                                )
                                            }
                                            // Completed — include all, no time restriction
                                            history.isCompleted -> {
                                                completedBucket.add(
                                                    pod.copy(
                                                        latestEpisode = freshEpisode,
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

                        // Build smart catch-up list: Unplayed first → In Progress by recency → All Completed by recency
                        val catchUpList = buildList {
                            addAll(unplayedBucket)
                            addAll(inProgressBucket.sortedByDescending { it.second }.map { it.first })
                            addAll(completedBucket.sortedByDescending { it.second }.map { it.first })
                        }

                        // B. New Episodes — Same Progressive Density
                        // 1 unplayed → 1 full card
                        // 2 unplayed → 2 full cards
                        // 3-5 unplayed → 1 full card + 1 grid card (2-4 items)
                        if (unplayedBucket.isNotEmpty()) {
                            val first = unplayedBucket.first()
                            heroList.add(
                                SmartHeroItem(
                                    type = HeroType.JUMP_BACK_IN,
                                    podcast = first,
                                    label = if (unplayedBucket.size == 1) "NEW EPISODE" else "FRESH DROP",
                                    description = first.latestEpisode?.title
                                )
                            )
                            usedPodcastIds.add(first.id)

                            if (unplayedBucket.size == 2) {
                                // 2 unplayed → second also gets a full card
                                val second = unplayedBucket[1]
                                heroList.add(
                                    SmartHeroItem(
                                        type = HeroType.JUMP_BACK_IN,
                                        podcast = second,
                                        label = "NEW EPISODE",
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
                        while (heroList.size < 8 && i < trendingList.size) {
                            val pod = trendingList[i]
                            if (!usedPodcastIds.contains(pod.id)) {
                                val label = when {
                                    i == 0 -> if (region == "in") "#1 IN INDIA" else "#1 IN US"
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
                            }
                            i++
                        }


                        // --- NEW: Unified Time Block ---
                        val blockConfig = getTimeBlockConfig()
                        val daySeed = java.time.LocalDate.now().toEpochDay()
                        
                        // Fetch sections in parallel
                        val sectionJobs = blockConfig.genres.map { genre ->
                             viewModelScope.async {
                                 try {
                                     // Use new Vibe API
                                     val list = podcastRepository.getCuratedPodcasts(genre.id) // genre.id is now a vibeId like "morning_news"
                                     val filtered = list
                                         .filter { it.latestEpisode != null }
                                         .shuffled(kotlin.random.Random(daySeed.toInt() + genre.title.hashCode()))
                                         .take(10)
                                     
                                     if (filtered.isNotEmpty()) {
                                         CuratedSectionData(genre.title, genre.id, filtered)
                                     } else null
                                 } catch (e: Exception) { null }
                             }
                        }
                        
                        val resolvedSections = sectionJobs.awaitAll().filterNotNull()
                        val timeBlock = if (resolvedSections.isNotEmpty()) {
                            CuratedTimeBlock(blockConfig.title, blockConfig.subtitle, blockConfig.icon, resolvedSections)
                        } else null
                        
                        // Update used IDs
                        timeBlock?.sections?.forEach { sec -> 
                            sec.podcasts.forEach { usedPodcastIds.add(it.id) } 
                        }

                        val remaining = trendingList.filter { !usedPodcastIds.contains(it.id) }
                        val discover = remaining 

                        if (trendingList.isNotEmpty()) {
                            cachedForYouTrending = trendingList
                            cachedHeroItems = heroList
                            cachedTimeBlock = timeBlock
                            cachedLatestEpisodes = catchUpList
                        }

                        _uiState.value = HomeUiState(
                            heroItems = heroList,
                            latestEpisodes = catchUpList,
                            unplayedEpisodeCount = unplayedBucket.size,
                            completedEpisodeCount = completedCount,
                            subscribedPodcasts = subs,
                            selectedCategory = _selectedCategory.value,
                            timeBlock = timeBlock,
                            discoverPodcasts = discover,
                            isLoading = false,
                            isFilterLoading = trendingList.isEmpty(),
                            isError = false
                        )
                }
            }
        }
        
        // --- CATEGORY OBSERVER (Considers Region) ---
        viewModelScope.launch {
            combine(_selectedCategory, userPrefs.regionStream) { category, region -> 
                category to region 
            }.collectLatest { (category, region) ->
                if (category == null) {
                    // "For You" - use cached data instantly (if matches region?)
                    // Simplified: caching matches current region because region change triggers base reload
                    if (cachedHeroItems.isNotEmpty()) {
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
    }
    
    fun toggleSubscription(podcastId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val podcast = state.timeBlock?.sections?.flatMap { it.podcasts }?.find { it.id == podcastId }
                ?: state.discoverPodcasts.find { it.id == podcastId }
                ?: state.heroItems.find { it.podcast.id == podcastId }?.podcast
            
            if (podcast != null) {
                subscriptionRepository.toggleSubscription(podcast)
                if (subscriptionRepository.isSubscribed(podcast.id)) {
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
                }
            }
        }
    }

    fun togglePlayback() {
        playbackRepository.togglePlayPause()
    }

    fun deleteHistoryItem(episodeId: String) {
        viewModelScope.launch {
            playbackRepository.deleteSession(episodeId)
        }
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
    
    // Debug Accessors
    val debugHistory = playbackRepository.getAllHistory()
    val debugPodcasts = subscriptionRepository.getAllSubscribedPodcasts()
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
        val isMonday = day == Calendar.MONDAY
        val isFriday = day == Calendar.FRIDAY

        return when (hour) {
            in 5..11 -> TimeBlockConfig(
                title = "Good Morning",
                subtitle = if(isWeekend) "Catch up on the week." else "Start your day with these updates.",
                icon = Icons.Rounded.WbSunny,
                genres = listOf(
                    GenreConfig("morning_news", "Top News"),
                    GenreConfig("morning_motivation", "Daily Motivation"), 
                    GenreConfig("business_insider", "Business & Tech")
                )
            )
            in 12..16 -> TimeBlockConfig(
                title = "Afternoon Break",
                subtitle = "Smart conversations to keep you going.",
                icon = Icons.Rounded.WbSunny,
                genres = listOf(
                    GenreConfig("science_explainer", "Science & Discovery"),
                    GenreConfig("tech_culture", "Tech & Gadgets"),
                    GenreConfig("creative_focus", "Creative Focus")
                )
            )
            in 17..22 -> TimeBlockConfig(
                title = "Evening Unwind",
                subtitle = if(isFriday) "Kick off the weekend." else "Relax, laugh, and catch up.",
                icon = Icons.Rounded.WbTwilight,
                genres = listOf(
                    GenreConfig("comedy_gold", "Comedy Gold"),
                    GenreConfig("tv_film_buff", "TV & Film"),
                    GenreConfig("sports_fan", "Sports Highlights")
                )
            )
            else -> TimeBlockConfig(
                title = "Late Night Listen",
                subtitle = "Stories for the dark hours.",
                icon = Icons.Rounded.NightsStay,
                genres = listOf(
                    GenreConfig("true_crime_sleep", "True Crime & Chill"),
                    GenreConfig("history_buff", "History"),
                    GenreConfig("mystery_thriller", "Mystery & Thrillers")
                )
            )
        }
    }
    
    fun resetFeatureFlag() {
        viewModelScope.launch {
            userPrefs.dismissFeatureAnnouncement("")
        }
    }
    
    override fun onCleared() {
        super.onCleared()
    }
}
