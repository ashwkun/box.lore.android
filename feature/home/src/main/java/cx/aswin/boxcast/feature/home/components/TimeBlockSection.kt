package cx.aswin.boxcast.feature.home.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.home.CuratedTimeBlock
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily

@Composable
fun TimeBlockSection(
    data: CuratedTimeBlock,
    onCuratedEpisodeClick: (Episode, Podcast, String, Int) -> Unit,
    onImpression: (String, List<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val themeColor = when (data.title) {
        "Good Morning" -> Color(0xFFFFA000)      // Soft Golden Amber
        "Afternoon Break" -> Color(0xFF0288D1)    // Sky Blue
        "Evening Unwind" -> Color(0xFFFFC107)     // Warm Yellow for evening
        "Late Night Listen" -> Color(0xFF2C3E50)  // Dark Night Slate Blue
        else -> MaterialTheme.colorScheme.primary
    }

    // Faint vertical gradient backdrop fading to transparent
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            themeColor.copy(alpha = 0.05f),
            Color.Transparent
        )
    )

    LaunchedEffect(data.title) {
        onImpression(data.title, data.sections.map { it.category })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Bleed background gradient to absolute screen edges, bypassing grid padding
            .layout { measurable, constraints ->
                val paddingPx = 16.dp.roundToPx()
                val expandedConstraints = constraints.copy(
                    maxWidth = constraints.maxWidth + (paddingPx * 2),
                    minWidth = constraints.minWidth + (paddingPx * 2)
                )
                val placeable = measurable.measure(expandedConstraints)
                layout(placeable.width - (paddingPx * 2), placeable.height) {
                    placeable.place(-paddingPx, 0)
                }
            }
            .background(gradientBrush)
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
    ) {
        // --- Master Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedTimeBlockIcon(title = data.title, themeColor = themeColor, fallbackIcon = data.icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = SectionHeaderFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = data.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // --- Genre Rails ---
        data.sections.forEachIndexed { index, section ->
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Rail
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(section.podcasts.size) { i ->
                        val podcast = section.podcasts[i]
                        val episode = podcast.latestEpisode
                        
                        if (episode != null) {
                            CuratedEpisodeCard(
                                podcast = podcast,
                                episode = episode,
                                onClick = {
                                    cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackCuratedCardTapped(
                                        podcastId = podcast.id,
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
private fun AnimatedTimeBlockIcon(title: String, themeColor: Color, fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector) {
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
                initialValue = -1f,
                targetValue = 10f,
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
                            .graphicsLayer { translationY = sunYOffset }
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
