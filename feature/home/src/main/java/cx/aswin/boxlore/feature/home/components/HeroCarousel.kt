package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableHeroList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroCarousel(
    heroItems: StableHeroList,
    onPlayClick: (Podcast, android.os.Bundle?) -> Unit,
    onDetailsClick: (Podcast) -> Unit,
    onArrowClick: (SmartHeroItem, Int) -> Unit,
    onToggleSubscription: (String) -> Unit,
    onTogglePlayback: (android.os.Bundle?) -> Unit,
    currentPlayingPodcastId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (heroItems.list.isEmpty()) return

    val carouselState = rememberCarouselState { heroItems.list.size }

    // Telemetry: Track Max Swipe Depth
    val maxScrolledIndex = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(maxScrolledIndex.intValue) {
        if (maxScrolledIndex.intValue > 0) {
            kotlinx.coroutines.delay(3000) // 3s debounce
            cx.aswin.boxlore.core.data.analytics.AnalyticsHelper.trackHomeHeroCarouselSwiped(
                maxCardIndexViewed = maxScrolledIndex.intValue,
                totalCardsAvailable = heroItems.list.size,
            )
        }
    }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 320.dp,
        itemSpacing = 16.dp,
        contentPadding = PaddingValues(0.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(420.dp),
    ) { i ->
        val item = heroItems.list[i]

        androidx.compose.runtime.DisposableEffect(i) {
            if (i > maxScrolledIndex.intValue) {
                maxScrolledIndex.intValue = i
            }
            onDispose {}
        }

        if (item.type == cx.aswin.boxlore.feature.home.HeroType.RESUME_GRID) {
            HeroGridCard(
                items = item.gridItems,
                title = "JUMP BACK IN",
                onPlayClick = { podcast ->
                    val bundle =
                        android.os.Bundle().apply {
                            putString("entry_point", "home_hero_resume_grid")
                            putInt("ep_carousel_position", i)
                            putString("ep_layout_type", "grid_card")
                            putBoolean("ep_is_subscribed", true) // Assuming RESUME grid items are subscribed
                        }
                    onPlayClick(podcast, bundle)
                },
                onDetailsClick = { podcast ->
                    // For grid items, we want Episode Details.
                    // We need to pass this action up.
                    // Let's assume onDetailsClick passed to HeroCarousel handles this.
                    onDetailsClick(podcast)
                },
                currentPlayingPodcastId = currentPlayingPodcastId,
                isPlaying = isPlaying,
                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),
            )
        } else if (item.type == cx.aswin.boxlore.feature.home.HeroType.NEW_EPISODES_GRID) {
            HeroGridCard(
                items = item.gridItems,
                title = "NEW EPISODES",
                onPlayClick = { podcast ->
                    val bundle =
                        android.os.Bundle().apply {
                            putString("entry_point", "home_hero_new_episodes_grid")
                            putInt("ep_carousel_position", i)
                            putString("ep_layout_type", "grid_card")
                            putBoolean("ep_is_subscribed", true)
                        }
                    onPlayClick(podcast, bundle)
                },
                onDetailsClick = { podcast ->
                    // Same details logic
                    onDetailsClick(podcast)
                },
                currentPlayingPodcastId = currentPlayingPodcastId,
                isPlaying = isPlaying,
                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),
            )
        } else {
            HeroCard(
                item = item,
                onClick = {
                    val bundle =
                        android.os.Bundle().apply {
                            putString("entry_point", "home_hero_${item.type.name.lowercase()}")
                            putInt("ep_carousel_position", i)
                            putString("ep_layout_type", "full_card")
                        }
                    if (currentPlayingPodcastId == item.podcast.id && isPlaying) {
                        onTogglePlayback(bundle)
                    } else {
                        onPlayClick(item.podcast, bundle)
                    }
                }, // Primary "Play" or "Pause" button action
                onArrowClick = { onArrowClick(item, i) },
                onToggleSubscription = { onToggleSubscription(item.podcast.id) },
                currentPlayingPodcastId = currentPlayingPodcastId,
                isPlaying = isPlaying,
                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),
            )
        }
    }
}
