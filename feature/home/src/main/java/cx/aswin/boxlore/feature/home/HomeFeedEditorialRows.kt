package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.feature.home.components.CuratedEpisodeCard
import cx.aswin.boxlore.feature.home.components.HomeChildHeaderTone
import cx.aswin.boxlore.feature.home.components.HomeChildSectionHeader

internal fun LazyStaggeredGridScope.editorialFeedItems(
    content: PodcastFeedContent,
    loadingState: PodcastFeedLoadingState,
    callbacks: HomeFeedCallbacks,
) {
    if (loadingState.isEditorialRowsLoading && content.editorialRows.list.isEmpty()) {
        item(
            span = StaggeredGridItemSpan.FullLine,
            key = "editorial_rows_skeleton",
            contentType = "editorial_rows_skeleton",
        ) {
            cx.aswin.boxlore.feature.home.components.EditorialRowsSkeleton()
        }
    }
    content.editorialRows.list.forEachIndexed { index, row ->
        item(
            span = StaggeredGridItemSpan.FullLine,
            key = "editorial_${row.providerId}",
            contentType = "editorial_row",
        ) {
            EditorialRow(
                row = row,
                tone =
                    if (index % 2 == 0) {
                        HomeChildHeaderTone.PRIMARY
                    } else {
                        HomeChildHeaderTone.TERTIARY
                    },
                isLast = index == content.editorialRows.list.lastIndex,
                callbacks = callbacks,
            )
        }
    }
}

@Composable
private fun EditorialRow(
    row: HomeEditorialRow,
    tone: HomeChildHeaderTone,
    isLast: Boolean,
    callbacks: HomeFeedCallbacks,
) {
    LaunchedEffect(row.providerId) {
        AnalyticsHelper.trackCuratedBlockImpression(
            blockTitle = row.title,
            vibeIds = listOf(row.providerId),
        )
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 20.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HomeChildSectionHeader(
            title = row.title,
            subtitle = row.subtitle,
            icon = row.icon.toHomeEditorialIcon(),
            tone = tone,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = row.podcasts,
                key = { _, podcast ->
                    "${row.providerId}:${podcast.id}:${podcast.latestEpisode?.id.orEmpty()}"
                },
            ) { position, podcast ->
                val episode = podcast.latestEpisode ?: return@itemsIndexed
                CuratedEpisodeCard(
                    podcast = podcast,
                    episode = episode,
                    onClick = {
                        AnalyticsHelper.trackCuratedCardTapped(
                            podcastId = podcast.id,
                            podcastName = podcast.title,
                            vibeId = row.providerId,
                            positionIndex = position,
                        )
                        callbacks.onEpisodeClick?.invoke(
                            episode,
                            podcast,
                            "home_editorial_${row.providerId}",
                        ) ?: callbacks.onPodcastClick(
                            podcast,
                            "home_editorial_${row.providerId}",
                            null,
                            position,
                        )
                    },
                    modifier = Modifier.width(156.dp),
                )
            }
        }
    }
}
