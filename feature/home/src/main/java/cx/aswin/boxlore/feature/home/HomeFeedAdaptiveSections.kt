package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.content.ContentCandidate
import cx.aswin.boxlore.core.catalog.content.ContentSection
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.components.CuratedEpisodeCard
import cx.aswin.boxlore.feature.home.components.HomeChildHeaderTone
import cx.aswin.boxlore.feature.home.components.HomeChildSectionHeader
import cx.aswin.boxlore.feature.home.components.PodcastCard
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.lazy.items as lazyRowItems

internal fun LazyStaggeredGridScope.adaptiveFeedItems(
    content: PodcastFeedContent,
    layout: PodcastFeedLayout,
    loadingState: PodcastFeedLoadingState,
    callbacks: HomeFeedCallbacks,
) {
    if (loadingState.isAdaptiveSectionsLoading && content.adaptiveSections.list.isEmpty()) {
        adaptiveSectionsSkeletonItem()
    }
    content.adaptiveSections.list.forEachIndexed { index, section ->
        adaptiveSectionItem(
            section = section,
            gridState = layout.gridState,
            showHeader = true,
            isLastInGroup = index == content.adaptiveSections.list.lastIndex,
            onAdaptiveSectionVisible = callbacks.onAdaptiveSectionVisible,
            onPodcastClick = callbacks.onPodcastClick,
            onEpisodeClick = callbacks.onEpisodeClick,
        )
    }
}

private fun LazyStaggeredGridScope.adaptiveSectionsSkeletonItem() {
    item(
        span = StaggeredGridItemSpan.FullLine,
        key = "adaptive_sections_skeleton",
        contentType = "adaptive_sections_skeleton",
    ) {
        cx.aswin.boxlore.feature.home.components.AdaptiveRailsSkeleton()
    }
}

internal fun LazyStaggeredGridScope.adaptiveSectionItem(
    section: ContentSection,
    gridState: LazyStaggeredGridState,
    showHeader: Boolean,
    isLastInGroup: Boolean,
    onAdaptiveSectionVisible: (ContentSection, Set<String>) -> Unit,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
) {
    item(
        span = StaggeredGridItemSpan.FullLine,
        key = "adaptive_${section.stableId}",
        contentType = "adaptive_section",
    ) {
        // Saveable row state (keyed by stableId) restores horizontal scroll without pinning
        // every adaptive rail in composition while the outer grid recycles items.
        AdaptiveSectionContent(
            section = section,
            gridState = gridState,
            showHeader = showHeader,
            isLastInGroup = isLastInGroup,
            onAdaptiveSectionVisible = onAdaptiveSectionVisible,
            onPodcastClick = onPodcastClick,
            onEpisodeClick = onEpisodeClick,
        )
    }
}

@Composable
internal fun AdaptiveSectionContent(
    section: ContentSection,
    gridState: LazyStaggeredGridState,
    showHeader: Boolean,
    isLastInGroup: Boolean,
    onAdaptiveSectionVisible: (ContentSection, Set<String>) -> Unit,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
) {
    val rowState =
        rememberSaveable(section.stableId, saver = LazyListState.Saver) {
            LazyListState()
        }
    AdaptiveSectionVisibilityEffect(
        section = section,
        gridState = gridState,
        rowState = rowState,
        onAdaptiveSectionVisible = onAdaptiveSectionVisible,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = if (isLastInGroup) 20.dp else 12.dp),
    ) {
        if (showHeader) {
            AdaptiveSectionHeader(section)
        }
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            lazyRowItems(
                items = section.items,
                key = ContentCandidate::id,
            ) { candidate ->
                AdaptiveCandidateCard(
                    candidate = candidate,
                    source = "home_adaptive_${section.intent.id}",
                    onPodcastClick = onPodcastClick,
                    onEpisodeClick = onEpisodeClick,
                )
            }
        }
    }
}

@Composable
internal fun AdaptiveSectionVisibilityEffect(
    section: ContentSection,
    gridState: LazyStaggeredGridState,
    rowState: LazyListState,
    onAdaptiveSectionVisible: (ContentSection, Set<String>) -> Unit,
) {
    val sectionKey = "adaptive_${section.stableId}"
    val currentOnAdaptiveSectionVisible = rememberUpdatedState(onAdaptiveSectionVisible)
    LaunchedEffect(section, gridState, rowState) {
        snapshotFlow {
            val sectionVisible =
                gridState.layoutInfo.visibleItemsInfo.any {
                    it.key == sectionKey
                }
            if (sectionVisible) {
                rowState.layoutInfo.visibleItemsInfo
                    .mapNotNull { it.key as? String }
                    .toSet()
            } else {
                emptySet()
            }
        }.distinctUntilChanged().collect { visibleCandidateIds ->
            if (visibleCandidateIds.isNotEmpty()) {
                currentOnAdaptiveSectionVisible.value(section, visibleCandidateIds)
            }
        }
    }
}

@Composable
internal fun AdaptiveSectionHeader(section: ContentSection) {
    HomeChildSectionHeader(
        title = section.intent.title,
        subtitle = section.intent.subtitle,
        icon = section.intent.icon.toHomeSectionIcon(),
        tone = HomeChildHeaderTone.TERTIARY,
    )
}

@Composable
internal fun AdaptiveCandidateCard(
    candidate: ContentCandidate,
    source: String,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
) {
    val episode = candidate.episode
    val onCandidateClick: () -> Unit = {
        if (episode == null) {
            onPodcastClick(candidate.podcast, source, null, null)
        } else {
            onEpisodeClick?.invoke(episode, candidate.podcast, source)
        }
    }
    if (episode == null) {
        PodcastCard(
            podcast = candidate.podcast,
            onClick = onCandidateClick,
            modifier = Modifier.width(156.dp),
            showGenreChip = false,
        )
    } else {
        CuratedEpisodeCard(
            podcast = candidate.podcast,
            episode = episode,
            onClick = onCandidateClick,
            modifier = Modifier.width(156.dp),
        )
    }
}
