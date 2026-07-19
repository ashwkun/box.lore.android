package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.components.BecauseYouLikeSection
import cx.aswin.boxlore.feature.home.components.GridSkeletonItem
import cx.aswin.boxlore.feature.home.components.HomeTopLevelSectionHeader
import cx.aswin.boxlore.feature.home.components.PodcastCard
import cx.aswin.boxlore.feature.home.components.forYouItems

internal fun LazyStaggeredGridScope.curatedForYouItems(
    content: PodcastFeedContent,
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    derivedState: PodcastFeedDerivedState,
) {
    if (!derivedState.hasBecauseYouLike && !derivedState.hasRecommendations) return
    curatedHeaderItem(callbacks)
    becauseYouLikeItem(feedState, recommendationState, playback, callbacks, derivedState)
    if (derivedState.hasRecommendations) {
        forYouItems(
            recommendations = content.recommendations,
            onEpisodeClick = { episode, podcast ->
                callbacks.onEpisodeClick?.invoke(episode, podcast, "home_for_you")
            },
            discoveryContextTitle = feedState.discoveryGreeting.title,
            showTasteHeader = derivedState.hasBecauseYouLike,
            isFallback = recommendationState.isRecommendationsFallback,
        )
    }
}

private fun LazyStaggeredGridScope.curatedHeaderItem(callbacks: HomeFeedCallbacks) {
    item(span = StaggeredGridItemSpan.FullLine, key = "curated_header", contentType = "section_header") {
        HomeTopLevelSectionHeader(
            title = "Curated For You",
            icon = Icons.Rounded.AutoAwesome,
            seeAllIcon = Icons.Rounded.ChevronRight,
            seeAllContentDescription = "See all curated recommendations",
            onSeeAllClick = {
                callbacks.onNavigateToExplore?.invoke(null, "home_for_you_see_all", "foryou")
            },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun LazyStaggeredGridScope.becauseYouLikeItem(
    feedState: PodcastFeedUiState,
    recommendationState: PodcastFeedRecommendationState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    derivedState: PodcastFeedDerivedState,
) {
    val podcast = feedState.seemsToLikePodcast ?: return
    if (!derivedState.hasBecauseYouLike) return
    item(span = StaggeredGridItemSpan.FullLine, key = "because_you_like", contentType = "because_you_like") {
        PinnedGridItemContent {
            BecauseYouLikeSection(
                podcast = podcast,
                recommendations = recommendationState.becauseYouLikeRecommendations,
                suggestedPodcasts = recommendationState.becauseYouLikePodcasts,
                currentPlayingEpisodeId = playback.player.currentPlayingEpisodeId,
                isPlaying = playback.player.isPlaying,
                onEpisodeClick = { episode, episodePodcast ->
                    callbacks.onEpisodeClick?.invoke(episode, episodePodcast, "home_because_you_like")
                },
                onPlayEpisode = { ep, pod -> callbacks.onPlayEpisode(ep, pod, PlaybackEntryPoint.GENERIC) },
                onPodcastClick = { clickedPodcast ->
                    callbacks.onPodcastClick(clickedPodcast, "home_because_you_like", null, null)
                },
                onChangePodcastClick = recommendationState.onChangePodcastClick,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

internal fun LazyStaggeredGridScope.discoveryGreetingItem(
    feedState: PodcastFeedUiState,
    callbacks: HomeFeedCallbacks,
) {
    item(
        span = StaggeredGridItemSpan.FullLine,
        key = "discovery_greeting",
        contentType = "discovery_greeting",
    ) {
        DiscoveryGreetingHeader(
            greeting = feedState.discoveryGreeting,
            onSeeAllClick = {
                callbacks.onNavigateToExplore?.invoke(null, "home_discovery_greeting", "foryou")
            },
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

internal fun LazyStaggeredGridScope.discoverFeedItems(
    feedState: PodcastFeedUiState,
    derivedState: PodcastFeedDerivedState,
    callbacks: HomeFeedCallbacks,
) {
    discoverHeaderItem(feedState, callbacks)
    if (derivedState.showDiscoverContent) {
        discoverPodcastItems(derivedState, feedState, callbacks)
        discoverViewMoreItem(feedState, callbacks)
    } else {
        items(6, key = { "discover_skel_$it" }, contentType = { "discover_skel" }) {
            GridSkeletonItem()
        }
    }
}

private fun LazyStaggeredGridScope.discoverHeaderItem(
    feedState: PodcastFeedUiState,
    callbacks: HomeFeedCallbacks,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "discover_header", contentType = "section_header") {
        cx.aswin.boxlore.feature.home.components.DiscoverSection(
            selectedCategory = feedState.selectedCategory,
            onCategorySelected = callbacks.onSelectCategory,
            onHeaderClick = { callbacks.onNavigateToExplore?.invoke(feedState.selectedCategory ?: "All", "home_discover_header", null) },
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

private fun LazyStaggeredGridScope.discoverPodcastItems(
    derivedState: PodcastFeedDerivedState,
    feedState: PodcastFeedUiState,
    callbacks: HomeFeedCallbacks,
) {
    itemsIndexed(
        derivedState.discoverItems,
        key = { _, podcast -> "discover_${podcast.id}" },
        contentType = { _, _ -> "discover_card" },
    ) { index, podcast ->
        PodcastCard(
            podcast = podcast,
            showGenreChip = derivedState.discoverGenreChip,
            onClick = { callbacks.onPodcastClick(podcast, "home_discover_grid", feedState.selectedCategory, index) },
        )
    }
}

private fun LazyStaggeredGridScope.discoverViewMoreItem(
    feedState: PodcastFeedUiState,
    callbacks: HomeFeedCallbacks,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "discover_view_more", contentType = "discover_view_more") {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.FilledTonalButton(
                onClick = { callbacks.onNavigateToExplore?.invoke(feedState.selectedCategory ?: "All", "home_discover_view_all_button", null) },
            ) {
                Text("View more in ${feedState.selectedCategory ?: "Explore"}")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DiscoveryGreetingHeader(
    greeting: DiscoveryGreeting,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (greeting.daypart) {
            ContentDaypart.MORNING,
            ContentDaypart.AFTERNOON,
            -> Icons.Rounded.WbSunny
            ContentDaypart.EVENING -> Icons.Rounded.WbTwilight
            ContentDaypart.LATE_NIGHT -> Icons.Rounded.NightsStay
        }
    HomeTopLevelSectionHeader(
        title = greeting.title,
        // Personalized rails are no longer daypart-specific; omit time-claiming subcopy.
        subtitle = null,
        icon = icon,
        seeAllIcon = Icons.Rounded.ChevronRight,
        seeAllContentDescription = "See all discoveries",
        onSeeAllClick = onSeeAllClick,
        modifier = modifier,
    )
}
