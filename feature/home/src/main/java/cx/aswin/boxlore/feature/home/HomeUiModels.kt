package cx.aswin.boxlore.feature.home

import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class SmartHeroItem(
    val type: HeroType,
    val podcast: Podcast,
    val label: String,
    val description: String? = null,
    val gridItems: List<Podcast> = emptyList(), // For RESUME_GRID
)

enum class HeroType { RESUME, RESUME_GRID, JUMP_BACK_IN, NEW_EPISODES_GRID, SPOTLIGHT }

enum class HomeEditorialIcon {
    HEADLINES,
    UPLIFTING,
    BUSINESS,
    SCIENCE,
    TECHNOLOGY,
    CREATIVITY,
    COMEDY,
    SCREEN,
    SPORTS,
    TRUE_CRIME,
    HISTORY,
    MYSTERY,
}

@Immutable
data class HomeEditorialRow(
    /** Stable legacy provider ID; never shown in listener-facing copy. */
    val providerId: String,
    val title: String,
    val subtitle: String,
    val icon: HomeEditorialIcon,
    val podcasts: List<Podcast>,
)

@Immutable
data class HomeUiState(
    val heroItems: List<SmartHeroItem>,
    val latestEpisodes: List<Podcast> = emptyList(), // "Latest" Section (Smart: Unplayed → In Progress → Completed)
    val unplayedEpisodeCount: Int = 0, // Badge count for "New Episodes" header
    val completedEpisodeCount: Int = 0,
    val subscribedPodcasts: List<Podcast> = emptyList(), // "Your Shows" Section
    val selectedCategory: String? = null, // Null = "For You"
    val discoveryGreeting: DiscoveryGreeting = discoveryGreetingFor(ContentDaypart.MORNING),
    val discoverPodcasts: List<Podcast>,
    val recommendations: List<Episode> = emptyList(), // Personalized recommendations from Qdrant
    val isLoading: Boolean = false, // Initial full-screen loader
    val isFilterLoading: Boolean = false, // Inline loader when switching genres
    val isError: Boolean = false,
    val selectedPodcastId: String? = null,
    val selectedPodcastEpisodes: List<Episode> = emptyList(),
    val isSelectedPodcastLoading: Boolean = false,
    val isSelectedRssRefreshing: Boolean = false,
    val episodePlaybackState: Map<String, Pair<EpisodeStatus, Float>> = emptyMap(),
    val showImportBanner: Boolean = false,
    val briefing: Briefing? = null,
    val briefingChapters: List<cx.aswin.boxlore.core.model.Chapter> = emptyList(),
    val isRecommendationsLoading: Boolean = true,
    val seemsToLikePodcast: Podcast? = null,
    val becauseYouLikeRecommendations: List<Episode> = emptyList(),
    val becauseYouLikePodcasts: List<Podcast> = emptyList(),
    val isBecauseYouLikeLoading: Boolean = false,
    val isRecommendationsFallback: Boolean = true,
    val editorialRows: List<HomeEditorialRow> = emptyList(),
    val isEditorialRowsLoading: Boolean = true,
)

/**
 * Shared switching state observable from any composable.
 * Used by MainActivity to hide the mini player during mode switch animation.
 */
object ModeSwitchState {
    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    fun start() {
        _isSwitching.value = true
    }

    fun finish() {
        _isSwitching.value = false
    }
}
