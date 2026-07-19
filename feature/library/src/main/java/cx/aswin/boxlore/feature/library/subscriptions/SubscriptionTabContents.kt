package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.data.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.ExpressiveSolarSystemEmptyState
import cx.aswin.boxlore.feature.library.PlayAllFab
import java.util.Locale

import cx.aswin.boxlore.feature.library.LocalLastSeenEpisodes

@Composable
internal fun ShowsTabContent(
    podcasts: List<Podcast>,
    onExploreClick: () -> Unit,
    onPodcastClick: (String) -> Unit,
    isGridView: Boolean
) {
    if (podcasts.isEmpty()) {
        ExpressiveSolarSystemEmptyState(
            title = "No Subscriptions Yet",
            description = "Follow your favorite podcasts to see them here.",
            actionText = "Find Podcasts",
            onExploreClick = onExploreClick
        )
    } else {
        val distinctGenres = remember(podcasts) { extractDistinctGenres(podcasts) }
        var selectedGenre by rememberSaveable { mutableStateOf("All") }
        val filteredPodcasts = remember(podcasts, selectedGenre) { filterPodcastsByGenre(podcasts, selectedGenre) }
        val distinctPodcasts = remember(filteredPodcasts) { filteredPodcasts.distinctBy { it.id } }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        SubscriptionGenreChips(
                            selectedGenre = selectedGenre,
                            onGenreChange = {
                                selectedGenre = it
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(it, "shows")
                            },
                            distinctGenres = distinctGenres
                        )
                    }
                }

                items(items = distinctPodcasts, key = { it.id }) { podcast ->
                    val lastSeenEpisodes = LocalLastSeenEpisodes.current
                    SubscriptionGridCard(
                        podcast = podcast,
                        lastSeenId = lastSeenEpisodes[podcast.id],
                        onClick = { onPodcastClick(podcast.id) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        SubscriptionGenreChips(
                            selectedGenre = selectedGenre,
                            onGenreChange = {
                                selectedGenre = it
                                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(it, "shows")
                            },
                            distinctGenres = distinctGenres,
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        )
                    }
                }

                items(items = distinctPodcasts, key = { it.id }) { podcast ->
                    SubscriptionListRow(
                        podcast = podcast,
                        onClick = { onPodcastClick(podcast.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LatestTabContent(
    podcasts: List<Podcast>,
    allHistory: List<ListeningHistoryEntity>,
    useSmartRank: Boolean,
    scoreEpisodes: suspend (
        List<Podcast>,
        List<ListeningHistoryEntity>,
    ) -> Map<String, Double>,
    onExploreClick: () -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    onPlayEpisode: ((Episode, Podcast) -> Unit)?,
    onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)? = null,
    isPlayerActive: Boolean = false
) {
    val episodePodcasts = remember(podcasts) {
        podcasts.filter { it.latestEpisode != null }
    }

    if (episodePodcasts.isEmpty()) {
        ExpressiveSolarSystemEmptyState(
            title = "No New Episodes",
            description = "You're all caught up! Explore for more content.",
            actionText = "Discover Shows",
            onExploreClick = onExploreClick
        )
    } else {
        val distinctGenres = remember(episodePodcasts) {
            episodePodcasts.flatMap { pod ->
                pod.genre.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.equals("podcast", ignoreCase = true) }
                    .map { genre ->
                        genre.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
            }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        var selectedGenre by remember { mutableStateOf("All") }

        val filteredEpisodePodcasts = remember(episodePodcasts, selectedGenre) {
            if (selectedGenre == "All") {
                episodePodcasts
            } else {
                episodePodcasts.filter { pod ->
                    pod.genre.split(",")
                        .map { it.trim() }
                        .any { it.equals(selectedGenre, ignoreCase = true) }
                }
            }
        }

        val episodeScores by produceState<Map<String, Double>>(
            initialValue = emptyMap(),
            filteredEpisodePodcasts,
            allHistory,
            useSmartRank,
        ) {
            value = if (useSmartRank) {
                scoreEpisodes(filteredEpisodePodcasts, allHistory)
            } else {
                emptyMap()
            }
        }

        val displayPodcasts = remember(filteredEpisodePodcasts, useSmartRank, episodeScores) {
            if (useSmartRank) {
                filteredEpisodePodcasts.sortedByDescending {
                    episodeScores[it.latestEpisode?.id] ?: 0.0
                }
            } else {
                filteredEpisodePodcasts.sortedByDescending { it.latestEpisode!!.publishedDate }
            }
        }

        val groupedEpisodes = remember(displayPodcasts, useSmartRank) {
            if (useSmartRank) {
                emptyMap()
            } else {
                displayPodcasts.groupBy { getChronologicalHeader(it.latestEpisode!!.publishedDate) }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 240.dp, top = 4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    SubscriptionGenreChips(
                        selectedGenre = selectedGenre,
                        onGenreChange = {
                            selectedGenre = it
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsGenreFiltered(it, "latest")
                        },
                        distinctGenres = distinctGenres,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                if (useSmartRank) {
                    items(items = displayPodcasts, key = { "${it.id}_latest_smart" }) { podcast ->
                        val episode = podcast.latestEpisode!!
                        LatestEpisodeRow(
                            episode = episode,
                            podcast = podcast,
                            onClick = { onEpisodeClick?.invoke(episode, podcast, "library_latest_episodes") },
                            onPlay = if (onPlayEpisode != null) {
                                { onPlayEpisode(episode, podcast) }
                            } else null
                        )
                    }
                } else {
                    groupedEpisodes.forEach { (header, podcastsInGroup) ->
                        stickyHeader {
                            DateHeader(text = header)
                        }
                        items(items = podcastsInGroup, key = { "${it.id}_latest_chrono" }) { podcast ->
                            val episode = podcast.latestEpisode!!
                            LatestEpisodeRow(
                                episode = episode,
                                podcast = podcast,
                                onClick = { onEpisodeClick?.invoke(episode, podcast, "library_latest_episodes") },
                                onPlay = if (onPlayEpisode != null) {
                                    { onPlayEpisode(episode, podcast) }
                                } else null
                            )
                        }
                    }
                }
            }

            if (displayPodcasts.isNotEmpty()) {
                PlayAllFab(
                    isPlayerActive = isPlayerActive,
                    onClick = {
                        val episodesToPlay = displayPodcasts.map { it.latestEpisode!! }
                        val firstPodcast = displayPodcasts.firstOrNull()
                        if (firstPodcast != null && onPlayEpisodes != null) {
                            onPlayEpisodes(episodesToPlay, firstPodcast)
                        }
                    }
                )
            }
        }
    }
}

