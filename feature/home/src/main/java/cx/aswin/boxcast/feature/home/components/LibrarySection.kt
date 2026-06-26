package cx.aswin.boxcast.feature.home.components

import cx.aswin.boxcast.core.designsystem.components.optimizedImageUrl
import cx.aswin.boxcast.core.designsystem.components.LogRecomposition
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import androidx.compose.material.icons.rounded.Close

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.EpisodeStatus
import cx.aswin.boxcast.core.model.Podcast

@Composable
fun YourShowsSection(
    subscribedPodcasts: List<Podcast>,
    latestEpisodes: List<Podcast>, // Enriched with latest episodes
    unplayedEpisodeCount: Int = 0,
    selectedPodcastId: String?,
    selectedPodcastEpisodes: List<Episode>,
    isSelectedPodcastLoading: Boolean,
    episodePlaybackState: Map<String, Pair<EpisodeStatus, Float>> = emptyMap(),
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    onPodcastSelected: (String?) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayMix: () -> Unit,
    onPlayEpisode: (Episode, Podcast) -> Unit,
    onViewLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    LogRecomposition(name = "YourShowsSection")
    if (subscribedPodcasts.isEmpty()) return


    val interleavedPodcasts = remember(subscribedPodcasts) {
        if (subscribedPodcasts.size > 9) {
            val result = mutableListOf<Podcast>()
            var i = 0
            while (i < subscribedPodcasts.size) {
                if (i + 1 < subscribedPodcasts.size) {
                    result.add(subscribedPodcasts[i + 1])
                }
                result.add(subscribedPodcasts[i])
                i += 2
            }
            result
        } else {
            subscribedPodcasts
        }
    }


    val filteredScrollState = rememberScrollState()
    val filteredScrollConnection = remember(filteredScrollState) {
        object : NestedScrollConnection {
            var isFirstScrollEvent = true
            var lockToChild = false
            var wasLocked = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    if (isFirstScrollEvent) {
                        isFirstScrollEvent = false
                        val isScrollingUp = available.y < 0f // dragging up / scrolling down
                        val isScrollingDown = available.y > 0f // dragging down / scrolling up
                        
                        val isAtTop = filteredScrollState.value == 0
                        val isAtBottom = filteredScrollState.value >= filteredScrollState.maxValue
                        
                        lockToChild = when {
                            isScrollingUp && isAtBottom -> false
                            isScrollingDown && isAtTop -> false
                            else -> true
                        }
                        wasLocked = lockToChild
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && lockToChild) {
                    // Slight pass: pass 10% to parent, consume 90%
                    return available * 0.9f
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                isFirstScrollEvent = true
                lockToChild = false
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (wasLocked) {
                    wasLocked = false
                    return available // Block momentum from scrolling parent if gesture started inside child
                }
                return Velocity.Zero
            }
        }
    }

    Column(modifier = modifier) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.expressiveClickable {
                    if (selectedPodcastId != null) {
                        onPodcastSelected(null) // clear selection
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bookmarks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Your Shows",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = SectionHeaderFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    letterSpacing = (-0.5).sp
                )
                if (selectedPodcastId != null && subscribedPodcasts.size > 1) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                            .clickable { onPodcastSelected(null) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filtered",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear Filter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            FilledTonalIconButton(
                onClick = onViewLibrary,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "View Library",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- The Selector Grid ---
        if (subscribedPodcasts.size <= 4) {
            LazyRow(
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subscribedPodcasts.size > 1) {
                    item(key = "mixtape") {
                        MixtapeSelectorCover(
                            isSelected = selectedPodcastId == null,
                            isAnyPodcastSelected = selectedPodcastId != null,
                            onClick = { onPodcastSelected(null) },
                            modifier = Modifier.size(60.dp).animateItem()
                        )
                    }
                }
                items(subscribedPodcasts, key = { it.id }) { podcast ->
                    SelectorCover(
                        podcast = podcast,
                        isSelected = selectedPodcastId == podcast.id,
                        isAnyPodcastSelected = selectedPodcastId != null,
                        onClick = {
                            if (subscribedPodcasts.size > 1) {
                                onPodcastSelected(if (selectedPodcastId == podcast.id) null else podcast.id)
                            }
                        },
                        modifier = Modifier.size(60.dp).animateItem()
                    )
                }
            }
        } else if (subscribedPodcasts.size <= 9) {
            val allItems = remember(subscribedPodcasts) {
                val list = mutableListOf<Any>()
                if (subscribedPodcasts.size > 1) {
                    list.add("mixtape")
                }
                list.addAll(subscribedPodcasts)
                list
            }
            val row1Items = remember(allItems) { allItems.take(5) }
            val row2Items = remember(allItems) { allItems.drop(5) }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .padding(bottom = 16.dp)
            ) {
                val containerWidth = maxWidth
                val availableWidth = containerWidth - 16.dp
                
                val itemSize = remember(availableWidth) {
                    val minSpacing = 6.dp
                    val neededWidthFor60 = (60.dp * 5) + (minSpacing * 4) // 324.dp
                    if (availableWidth < neededWidthFor60) {
                        ((availableWidth - (minSpacing * 4)) / 5).coerceAtLeast(48.dp)
                    } else {
                        60.dp
                    }
                }
                
                val calculatedSpacing = remember(availableWidth, itemSize) {
                    val remaining = availableWidth - (itemSize * 5)
                    if (remaining > 0.dp) {
                        (remaining / 4).coerceAtMost(16.dp).coerceAtLeast(6.dp)
                    } else {
                        6.dp
                    }
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(calculatedSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row1Items.forEach { item ->
                            val itemModifier = Modifier.size(itemSize)
                            if (item is String && item == "mixtape") {
                                MixtapeSelectorCover(
                                    isSelected = selectedPodcastId == null,
                                    isAnyPodcastSelected = selectedPodcastId != null,
                                    onClick = { onPodcastSelected(null) },
                                    modifier = itemModifier
                                )
                            } else if (item is Podcast) {
                                val hasRecentNew = remember(item.subscribedAt, item.latestEpisode?.publishedDate) {
                                    val ep = item.latestEpisode
                                    if (ep != null && item.subscribedAt > 0L && ep.publishedDate > (item.subscribedAt / 1000L)) {
                                        val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0
                                        hoursSinceRelease <= 48.0
                                    } else false
                                }
                                SelectorCover(
                                    podcast = item,
                                    isSelected = selectedPodcastId == item.id,
                                    isAnyPodcastSelected = selectedPodcastId != null,
                                    hasRecentNew = hasRecentNew,
                                    onClick = {
                                        onPodcastSelected(if (selectedPodcastId == item.id) null else item.id)
                                    },
                                    modifier = itemModifier
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(calculatedSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row2Items.forEach { item ->
                            val itemModifier = Modifier.size(itemSize)
                            if (item is Podcast) {
                                val hasRecentNew = remember(item.subscribedAt, item.latestEpisode?.publishedDate) {
                                    val ep = item.latestEpisode
                                    if (ep != null && item.subscribedAt > 0L && ep.publishedDate > (item.subscribedAt / 1000L)) {
                                        val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0
                                        hoursSinceRelease <= 48.0
                                    } else false
                                }
                                SelectorCover(
                                    podcast = item,
                                    isSelected = selectedPodcastId == item.id,
                                    isAnyPodcastSelected = selectedPodcastId != null,
                                    hasRecentNew = hasRecentNew,
                                    onClick = {
                                        onPodcastSelected(if (selectedPodcastId == item.id) null else item.id)
                                    },
                                    modifier = itemModifier
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .height(156.dp) // 60 + 60 + 12 + 4 + 4 = 140 + 16 (bottom padding) = 156
                    .padding(bottom = 16.dp)
            ) {
                item(key = "mixtape") {
                    MixtapeSelectorCover(
                        isSelected = selectedPodcastId == null,
                        isAnyPodcastSelected = selectedPodcastId != null,
                        onClick = { onPodcastSelected(null) },
                        modifier = Modifier.size(60.dp).animateItem()
                    )
                }
                items(interleavedPodcasts, key = { it.id }) { podcast ->
                    val hasRecentNew = remember(podcast.subscribedAt, podcast.latestEpisode?.publishedDate) {
                        val ep = podcast.latestEpisode
                        if (ep != null && podcast.subscribedAt > 0L && ep.publishedDate > (podcast.subscribedAt / 1000L)) {
                            val hoursSinceRelease = (System.currentTimeMillis() / 1000.0 - ep.publishedDate) / 3600.0
                            hoursSinceRelease <= 48.0
                        } else false
                    }
                    SelectorCover(
                        podcast = podcast,
                        isSelected = selectedPodcastId == podcast.id,
                        isAnyPodcastSelected = selectedPodcastId != null,
                        hasRecentNew = hasRecentNew,
                        onClick = {
                            onPodcastSelected(if (selectedPodcastId == podcast.id) null else podcast.id)
                        },
                        modifier = Modifier.size(60.dp).animateItem()
                    )
                }
            }
        }

        // --- Dynamic Content Area ---
        OutlinedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .animateContentSize() // Animates height changes smoothly with a spring curve!
        ) {
            AnimatedContent(
                targetState = selectedPodcastId == null && subscribedPodcasts.size > 1,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                    fadeOut(animationSpec = tween(90))
                },
                label = "shows_mode_transition",
                modifier = Modifier.fillMaxWidth()
            ) { isMixtapeMode ->
                if (isMixtapeMode) {
                    // Scenario A: Default State (More than 1 Sub, Nothing Selected)
                    val displayList = latestEpisodes

                    if (displayList.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                    )
                                )
                                .padding(vertical = 16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Text(
                                                text = "Up Next",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = (-0.5).sp,
                                                    fontSize = 21.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    if (unplayedEpisodeCount > 0) {
                                        Button(
                                            onClick = onPlayMix,
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Play All",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Play All",
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 0.2.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(displayList, key = { it.latestEpisode?.id ?: it.id }) { podcast ->
                                        val episode = podcast.latestEpisode!!
                                        val state = episodePlaybackState[episode.id]
                                        MixtapeEpisodeCard(
                                            episode = episode,
                                            podcast = podcast,
                                            onClick = { onEpisodeClick(episode, podcast, "home_mixtape_episodes") },
                                            onPlay = { onPlayEpisode(episode, podcast) },
                                            overrideStatus = state?.first,
                                            overrideProgress = state?.second,
                                            currentPlayingEpisodeId = currentPlayingEpisodeId,
                                            isPlaying = isPlaying
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Caught up state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "You're all caught up",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "New drops and active sessions will show up here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    // Scenario B: Filtered State (A Specific Sub is Selected) / Scenario C: Only 1 Sub Edge Case
                    val activeId = selectedPodcastId ?: subscribedPodcasts.firstOrNull()?.id
                    val selectedPodcast = subscribedPodcasts.find { it.id == activeId }
                    
                    if (selectedPodcast == null) {
                        Text(
                            text = "No episodes available",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .expressiveClickable { onPodcastClick(selectedPodcast) }
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OptimizedImage(
                                        url = (selectedPodcast.imageUrl.takeIf { it.isNotEmpty() } ?: selectedPodcast.fallbackImageUrl),
                                        proxyWidth = 88,
                                        contentDescription = selectedPodcast.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedPodcast.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = (-0.4).sp,
                                                fontSize = 17.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val isOldest = (selectedPodcast.preferredSort ?: "newest") == "oldest"
                                        Text(
                                            text = "Tap for show info • ${if (isOldest) "Next Up" else "Latest Drops"}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Show info",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                thickness = 1.dp
                            )
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            if (isSelectedPodcastLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BoxLoreLoader.Expressive(size = 48.dp)
                                }
                            } else if (selectedPodcastEpisodes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No episodes available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 324.dp) // Self-sizing height wraps content for fewer episodes! Eliminates empty space.
                                        .nestedScroll(filteredScrollConnection)
                                        .verticalScroll(filteredScrollState),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val latestTwoIds = remember(selectedPodcastEpisodes) {
                                        selectedPodcastEpisodes.sortedByDescending { it.publishedDate }.take(2).map { it.id }.toSet()
                                    }
                                    selectedPodcastEpisodes.take(8).forEach { episode ->
                                        val state = episodePlaybackState[episode.id]
                                        DenseEpisodeRow(
                                            episode = episode,
                                            podcast = selectedPodcast,
                                            onClick = { onEpisodeClick(episode, selectedPodcast, "home_filtered_latest_episodes") },
                                            onPlay = { onPlayEpisode(episode, selectedPodcast) },
                                            showPodcastTitle = false,
                                            overrideStatus = state?.first,
                                            overrideProgress = state?.second,
                                            currentPlayingEpisodeId = currentPlayingEpisodeId,
                                            isPlaying = isPlaying,
                                            isEligibleForNewTag = episode.id in latestTwoIds
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(40.dp)
                                            .fillMaxWidth()
                                            .expressiveClickable { onPodcastClick(selectedPodcast) }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "See All Episodes",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.1.sp
                                                ),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorCover(
    podcast: Podcast,
    isSelected: Boolean,
    isAnyPodcastSelected: Boolean,
    hasRecentNew: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(60.dp)
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 0.95f, label = "scale")
    val alpha by animateFloatAsState(targetValue = if (isSelected) 1f else if (isAnyPodcastSelected) 0.6f else 1f, label = "alpha")
    val cornerRadius by animateDpAsState(targetValue = if (isSelected) 16.dp else 12.dp, label = "cornerRadius")
    val borderStrokeWidth by animateDpAsState(targetValue = if (isSelected) 3.dp else 0.dp, label = "borderStrokeWidth")

    // Slow shimmer animation across the NEW badge background (4 seconds loop)
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -100f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    val baseColor = MaterialTheme.colorScheme.primary
    val shimmerColor = MaterialTheme.colorScheme.primaryContainer
    
    val brush = remember(shimmerOffset, baseColor, shimmerColor) {
        Brush.linearGradient(
            colors = listOf(
                baseColor,
                shimmerColor,
                baseColor
            ),
            start = Offset(shimmerOffset, 0f),
            end = Offset(shimmerOffset + 80f, 0f)
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
    ) {
        // Inner Box to apply clipping and clickable to the cover image container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .expressiveClickable(
                    shape = RoundedCornerShape(cornerRadius),
                    onClick = onClick
                )
                .clip(RoundedCornerShape(cornerRadius))
        ) {
            OptimizedImage(
                url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
                proxyWidth = 120,
                contentDescription = podcast.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alpha)
            )
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(borderStrokeWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius))
                )
            }
        }

        // New episode "NEW" text chip indicator (overlapping the top right corner) with a slow shimmer effect
        if (hasRecentNew && !isSelected) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Transparent,
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-4).dp)
                    .background(brush, RoundedCornerShape(6.dp))
            ) {
                Text(
                    text = "NEW",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        lineHeight = 8.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DenseEpisodeRow(
    episode: Episode,
    podcast: Podcast,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    showPodcastTitle: Boolean = true,
    overrideStatus: EpisodeStatus? = null,
    overrideProgress: Float? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    isEligibleForNewTag: Boolean = true
) {
    val status = overrideStatus ?: if (podcast.latestEpisode?.id == episode.id) podcast.episodeStatus else EpisodeStatus.UNPLAYED
    val progress = overrideProgress ?: if (podcast.latestEpisode?.id == episode.id) (podcast.resumeProgress ?: 0f) else 0f
    val isCompleted = status == EpisodeStatus.COMPLETED
    val isInProgress = status == EpisodeStatus.IN_PROGRESS
    val isCurrentPlaying = currentPlayingEpisodeId == episode.id && isPlaying

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .expressiveClickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            OptimizedImage(
                url = (episode.imageUrl?.takeIf { it.isNotEmpty() } ?: podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl),
                proxyWidth = 112,
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            // Transparent mini glassmorphic play button centered on artwork
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .expressiveClickable(onClick = onPlay)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isCurrentPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Played",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(8.dp)
                    )
                }
            }


            if (isInProgress && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.4f),
                    drawStopIndicator = {}
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showPodcastTitle) {
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                val isNew = isEligibleForNewTag && status == EpisodeStatus.UNPLAYED && podcast.subscribedAt > 0L && episode.publishedDate > (podcast.subscribedAt / 1000L - 7 * 24 * 3600L)
                if (isNew) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 8.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                val dateText = formatRelativeDate(episode.publishedDate)
                if (dateText.isNotEmpty()) {
                    val prefix = if (showPodcastTitle || isNew) "• " else ""
                    Text(
                        text = "$prefix$dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                if (episode.duration > 0) {
                    val h = episode.duration / 3600
                    val m = (episode.duration % 3600) / 60
                    val displayText = if (isInProgress && progress > 0f) {
                        val remaining = ((1f - progress) * episode.duration).toInt()
                        val rh = remaining / 3600
                        val rm = (remaining % 3600) / 60
                        if (rh > 0) "${rh}h ${rm}m left" else "${rm}m left"
                    } else {
                        if (h > 0) "${h}h ${m}m" else "${m}m"
                    }
                    Text(
                        text = "• $displayText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isInProgress) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = if (isInProgress) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

private fun formatRelativeDate(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSeconds
    return when {
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        diff < 2592000 -> "${diff / 604800}w ago"
        diff < 31536000 -> "${diff / 2592000}mo ago"
        else -> "${diff / 31536000}y ago"
    }
}

@Composable
private fun MixtapeSelectorCover(
    isSelected: Boolean,
    isAnyPodcastSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(60.dp)
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 0.95f, label = "scale")
    val alpha by animateFloatAsState(targetValue = if (isSelected) 1f else if (isAnyPodcastSelected) 0.6f else 1f, label = "alpha")
    val cornerRadius by animateDpAsState(targetValue = if (isSelected) 16.dp else 12.dp, label = "cornerRadius")
    val borderStrokeWidth by animateDpAsState(targetValue = if (isSelected) 3.dp else 0.dp, label = "borderStrokeWidth")

    Box(
        modifier = modifier
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .expressiveClickable(
                    shape = RoundedCornerShape(cornerRadius),
                    onClick = onClick
                )
                .clip(RoundedCornerShape(cornerRadius))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .alpha(alpha),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "For You",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(borderStrokeWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius))
                )
            }
        }
    }
}

@Composable
private fun MixtapeEpisodeCard(
    episode: Episode,
    podcast: Podcast,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    overrideStatus: EpisodeStatus? = null,
    overrideProgress: Float? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false
) {
    val status = overrideStatus ?: if (podcast.latestEpisode?.id == episode.id) podcast.episodeStatus else EpisodeStatus.UNPLAYED
    val progress = overrideProgress ?: if (podcast.latestEpisode?.id == episode.id) (podcast.resumeProgress ?: 0f) else 0f
    val isInProgress = status == EpisodeStatus.IN_PROGRESS
    val isCompleted = status == EpisodeStatus.COMPLETED
    val isCurrentPlaying = currentPlayingEpisodeId == episode.id && isPlaying

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
            .width(280.dp)
            .height(96.dp)
            .expressiveClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Clean cover art with centered transparent glassmorphic play button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                OptimizedImage(
                    url = (episode.imageUrl?.takeIf { it.isNotEmpty() } ?: podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl),
                    proxyWidth = 152,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Transparent glass circular play button overlaid directly on artwork
                Surface(
                    onClick = onPlay,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp)
                        .expressiveClickable(onClick = onPlay)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isCurrentPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (isCompleted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Played",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                if (isInProgress && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.5.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.4f),
                        drawStopIndicator = {}
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center: Info Column (Highly structured vertical layout with spacedBy to prevent stretching)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            lineHeight = 15.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isNew = status == EpisodeStatus.UNPLAYED && podcast.subscribedAt > 0L && episode.publishedDate > (podcast.subscribedAt / 1000L - 7 * 24 * 3600L)
                    if (isNew) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 7.5.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    val relativeDate = formatRelativeDate(episode.publishedDate)
                    if (relativeDate.isNotEmpty()) {
                        val prefix = if (isNew) "• " else ""
                        Text(
                            text = "$prefix$relativeDate",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }

                    if (episode.duration > 0) {
                        val h = episode.duration / 3600
                        val m = (episode.duration % 3600) / 60
                        val timeText = if (isInProgress && progress > 0f) {
                            val remaining = ((1f - progress) * episode.duration).toInt()
                            val rm = (remaining % 3600) / 60
                            "${rm}m left"
                        } else {
                            if (h > 0) "${h}h ${m}m" else "${m}m"
                        }
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isInProgress) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

