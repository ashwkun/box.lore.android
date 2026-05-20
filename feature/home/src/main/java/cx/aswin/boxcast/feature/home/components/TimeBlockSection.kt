package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.feature.home.CuratedTimeBlock
import cx.aswin.boxcast.core.designsystem.theme.SectionHeaderFontFamily


import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color


@Composable
fun TimeBlockSection(
    data: CuratedTimeBlock,
    onCuratedEpisodeClick: (Episode, Podcast, String, Int) -> Unit,
    onImpression: (String, List<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val themeColor = when (data.title) {
        "Good Morning" -> MaterialTheme.colorScheme.primary
        "Afternoon Break" -> MaterialTheme.colorScheme.secondary
        "Evening Unwind" -> MaterialTheme.colorScheme.tertiary
        "Late Night Listen" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            themeColor.copy(alpha = 0.08f),
            Color.Transparent
        )
    )

    LaunchedEffect(data.title) {
        onImpression(data.title, data.sections.map { it.category })
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            // --- Master Header ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = data.icon,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = SectionHeaderFontFamily,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = data.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Genre Rails ---
            data.sections.forEachIndexed { index, section ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    themeColor.copy(alpha = 0.08f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = section.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = themeColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
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
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
