package cx.aswin.boxlore.feature.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.components.HeroCarousel

@androidx.compose.runtime.Stable
internal data class PodcastFeedContent(
    val heroItems: StableHeroList,
    val latestItems: StablePodcastList,
    val subscribedItems: StablePodcastList,
    val adaptiveSections: StableContentSectionList,
    val gridItems: StablePodcastList,
    val recommendations: StableEpisodeList,
    val selectedPodcastEpisodes: StableEpisodeList,
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedUiState(
    val discoveryGreeting: DiscoveryGreeting,
    val selectedCategory: String?,
    val selectedPodcastId: String?,
    val briefing: Briefing?,
    val briefingChapters: List<cx.aswin.boxlore.core.model.Chapter>,
    val seemsToLikePodcast: Podcast?,
    val showImportBanner: Boolean,
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedRecommendationState(
    val becauseYouLikeRecommendations: StableEpisodeList,
    val becauseYouLikePodcasts: StablePodcastList,
    val isRecommendationsLoading: Boolean = true,
    val isRecommendationsFallback: Boolean = true,
    val onChangePodcastClick: () -> Unit = {},
    val tasteSectionTitle: String = "",
    val tasteSectionSubtitle: String = "",
    val activeMission: cx.aswin.boxlore.core.catalog.home.HomeDiscoveryMission? = null,
    val missionEpisodes: StableEpisodeList = StableEpisodeList(emptyList()),
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedLoadingState(
    val isAdaptiveSectionsLoading: Boolean = false,
    val isFilterLoading: Boolean,
    val isSelectedPodcastLoading: Boolean = false,
    val isSelectedRssRefreshing: Boolean = false,
    val isLoading: Boolean,
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedPlayback(
    val player: HomePlaybackUi,
    val episodePlaybackState: StablePlaybackStateMap,
)

@androidx.compose.runtime.Stable
internal data class PodcastFeedLayout(
    val gridState: LazyStaggeredGridState,
    val modifier: Modifier = Modifier,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PodcastFeed(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
    loadingState: PodcastFeedLoadingState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    layout: PodcastFeedLayout,
) {
    val context = LocalContext.current
    val derivedState = rememberPodcastFeedDerivedState(content, feedState, recommendationState, loadingState)
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        state = layout.gridState,
        modifier = layout.modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
    ) {
        smartHeroItem(content, playback, callbacks, derivedState)
        yourShowsItem(content, feedState, loadingState, playback, callbacks, derivedState)
        dailyBriefingItem(feedState, playback, callbacks, context)
        curatedForYouItems(content, feedState, recommendationState, playback, callbacks, derivedState)
        discoveryGreetingItem(feedState, callbacks)
        missionRailItems(recommendationState, callbacks)
        adaptiveFeedItems(content, layout, loadingState, callbacks)
        discoverFeedItems(feedState, derivedState, callbacks)
    }
}

internal data class PodcastFeedDerivedState(
    val viewportReady: Boolean,
    val heroLoaded: Boolean,
    val hasBecauseYouLike: Boolean,
    val hasRecommendations: Boolean,
    val discoverItems: List<Podcast>,
    val showDiscoverContent: Boolean,
    val discoverGenreChip: Boolean,
)

@Composable
private fun rememberPodcastFeedDerivedState(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
    loadingState: PodcastFeedLoadingState,
): PodcastFeedDerivedState {
    val viewportReady = !loadingState.isLoading
    val discoverItems =
        remember(content.gridItems.list, feedState.selectedCategory) {
            content.gridItems.list.distinctBy { it.id }.take(10)
        }
    return PodcastFeedDerivedState(
        viewportReady = viewportReady,
        heroLoaded = viewportReady && content.heroItems.list.isNotEmpty(),
        hasBecauseYouLike = hasBecauseYouLike(feedState, recommendationState),
        hasRecommendations = recommendationState.isRecommendationsLoading || content.recommendations.list.isNotEmpty(),
        discoverItems = discoverItems,
        showDiscoverContent = !loadingState.isLoading && !loadingState.isFilterLoading && discoverItems.isNotEmpty(),
        discoverGenreChip = feedState.selectedCategory == null,
    )
}

private fun hasBecauseYouLike(
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
): Boolean =
    feedState.seemsToLikePodcast != null &&
        (recommendationState.becauseYouLikeRecommendations.list.isNotEmpty() ||
            recommendationState.becauseYouLikePodcasts.list.isNotEmpty())

private fun LazyStaggeredGridScope.smartHeroItem(
    content: PodcastFeedContent,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    derivedState: PodcastFeedDerivedState,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "hero", contentType = "hero") {
        PinnedGridItemContent {
            androidx.compose.animation.Crossfade(
                targetState = derivedState.heroLoaded,
                animationSpec = tween(500),
                label = "hero_crossfade",
                modifier = Modifier.padding(bottom = 12.dp),
            ) { loaded ->
                if (loaded) {
                    SmartHeroCarousel(content.heroItems, playback.player, callbacks)
                } else {
                    cx.aswin.boxlore.feature.home.components.HeroSkeleton()
                }
            }
        }
    }
}

@Composable
internal fun PinnedGridItemContent(content: @Composable () -> Unit) {
    val pinnable = androidx.compose.ui.layout.LocalPinnableContainer.current
    androidx.compose.runtime.DisposableEffect(pinnable) {
        val handle = pinnable?.pin()
        onDispose { handle?.release() }
    }
    content()
}

@Composable
private fun SmartHeroCarousel(
    heroItems: StableHeroList,
    playback: HomePlaybackUi,
    callbacks: HomeFeedCallbacks,
) {
    HeroCarousel(
        heroItems = heroItems,
        currentPlayingPodcastId = playback.currentPlayingPodcastId,
        isPlaying = playback.isPlaying,
        onPlayClick = { podcast, bundle -> callbacks.onPlayClick?.invoke(podcast, bundle) },
        onDetailsClick = { podcast -> handleHeroDetailsClick(podcast, callbacks) },
        onArrowClick = callbacks.onHeroArrowClick,
        onToggleSubscription = callbacks.onToggleSubscription,
        onTogglePlayback = callbacks.onTogglePlayback,
        modifier = Modifier,
    )
}

private fun handleHeroDetailsClick(
    podcast: Podcast,
    callbacks: HomeFeedCallbacks,
) {
    val episode = podcast.latestEpisode
    if (episode != null) {
        callbacks.onEpisodeClick?.invoke(episode, podcast, "home_hero_card")
    } else {
        callbacks.onPodcastClick(podcast, "home_hero_card", null, null)
    }
}
