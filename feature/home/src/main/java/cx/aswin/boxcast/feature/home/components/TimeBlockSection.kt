package cx.aswin.boxcast.feature.home.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.home.CuratedTimeBlock
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily

import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SportsBaseball
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.automirrored.outlined.TrendingUp

@Composable
private fun getGenreStyle(categoryId: String): ImageVector {
    return when (categoryId) {
        "morning_news" -> Icons.Outlined.Newspaper
        "morning_motivation" -> Icons.Outlined.Bolt
        "business_insider" -> Icons.AutoMirrored.Outlined.TrendingUp
        "science_explainer" -> Icons.Outlined.Science
        "tech_culture" -> Icons.Outlined.Memory
        "creative_focus" -> Icons.Outlined.Palette
        "comedy_gold" -> Icons.Outlined.EmojiEmotions
        "tv_film_buff" -> Icons.Outlined.Movie
        "sports_fan" -> Icons.Outlined.SportsBaseball
        "true_crime_sleep" -> Icons.Outlined.Fingerprint
        "history_buff" -> Icons.Outlined.AccountBalance
        "mystery_thriller" -> Icons.Outlined.Visibility
        else -> Icons.Outlined.AutoAwesome
    }
}

@Composable
fun TimeBlockSection(
    data: CuratedTimeBlock,
    onCuratedEpisodeClick: (Episode, Podcast, String, Int) -> Unit,
    onImpression: (String, List<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    LaunchedEffect(data.title) {
        onImpression(data.title, data.sections.map { it.category })
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // --- Genre Rails ---
        data.sections.forEachIndexed { index, section ->
            Column {
                val icon = getGenreStyle(section.category)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp, bottom = 14.dp) // Premium breathing room!
                ) {
                    // Clean, standard floating icon matching Discover & OnTheRise section headers exactly
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, // Unified M3 primary theme color
                        modifier = Modifier.size(22.dp) // 22dp is perfect for junior curated rails subheaders
                    )
                    
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.1).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Rail
                val distinctPodcasts = remember(section.podcasts) { section.podcasts.distinctBy { it.id } }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = distinctPodcasts.size,
                        key = { i -> distinctPodcasts[i].id }
                    ) { i ->
                        val podcast = distinctPodcasts[i]
                        val episode = podcast.latestEpisode
                        
                        if (episode != null) {
                            CuratedEpisodeCard(
                                podcast = podcast,
                                episode = episode,
                                onClick = {
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackCuratedCardTapped(
                                        podcastId = podcast.id,
                                        podcastName = podcast.title,
                                        vibeId = section.category,
                                        positionIndex = i
                                    )
                                    onCuratedEpisodeClick(episode, podcast, section.category, i)
                                }
                            )
                        }
                    }
                }
            }
            
            if (index < data.sections.size - 1) {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun AnimatedTimeBlockIcon(title: String, themeColor: Color, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    when (title) {
        "Good Morning", "Afternoon Break" -> {
            val infiniteTransition = rememberInfiniteTransition(label = "sunrise")
            val rayRotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(12000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "ray_rotation"
            )
            val sunPulse by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sun_pulse"
            )

            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = themeColor.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = rayRotation }
                )
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer {
                            scaleX = sunPulse
                            scaleY = sunPulse
                        }
                        .background(themeColor, CircleShape)
                )
            }
        }
        "Evening Unwind" -> {
            val infiniteTransition = rememberInfiniteTransition(label = "sunset")
            val sunYOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3200, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "sun_y"
            )

            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 20.dp)
                        .align(Alignment.TopCenter)
                        .clipToBounds()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WbSunny,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.TopCenter)
                            .graphicsLayer { translationY = sunYOffset.dp.toPx() }
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(themeColor.copy(alpha = 0.8f))
                )
            }
        }
        "Late Night Listen" -> {
            val infiniteTransition = rememberInfiniteTransition(label = "stars")
            val star1Alpha by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "star1"
            )
            val star2Alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1900, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "star2"
            )

            Box(modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.NightsStay,
                    contentDescription = null,
                    tint = themeColor,
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.CenterStart)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(5.dp)
                        .graphicsLayer { alpha = star1Alpha }
                        .background(themeColor, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 6.dp, end = 4.dp)
                        .size(3.5.dp)
                        .graphicsLayer { alpha = star2Alpha }
                        .background(themeColor, CircleShape)
                )
            }
        }
        else -> {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
