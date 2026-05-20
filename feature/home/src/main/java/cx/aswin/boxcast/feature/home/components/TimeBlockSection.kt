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
    val headerColor = when (data.title) {
        "Good Morning" -> Color(0xFFFFB300)      // Amber Gold
        "Afternoon Break" -> Color(0xFF0288D1)    // Sky Blue
        "Evening Unwind" -> Color(0xFF9C27B0)     // Sunset Purple
        "Late Night Listen" -> Color(0xFF3F51B5)  // Midnight Indigo
        else -> MaterialTheme.colorScheme.primary
    }

    val gradientBrush = when (data.title) {
        "Good Morning" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFD54F).copy(alpha = 0.22f),
                Color.Transparent
            )
        )
        "Afternoon Break" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF80D8FF).copy(alpha = 0.22f),
                Color.Transparent
            )
        )
        "Evening Unwind" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFFEA80FC).copy(alpha = 0.25f),
                Color.Transparent
            )
        )
        "Late Night Listen" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF8C9EFF).copy(alpha = 0.25f),
                Color.Transparent
            )
        )
        else -> null
    }

    LaunchedEffect(data.title) {
        onImpression(data.title, data.sections.map { it.category })
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        border = BorderStroke(
            1.5.dp,
            headerColor.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (gradientBrush != null) {
                        Modifier.background(gradientBrush)
                    } else {
                        Modifier
                    }
                )
                .padding(18.dp)
        ) {
            // --- Master Header ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(headerColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = data.icon,
                        contentDescription = null,
                        tint = headerColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = SectionHeaderFontFamily,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
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
                        Box(
                            modifier = Modifier
                                .background(
                                    headerColor.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.small
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = section.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = headerColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                letterSpacing = 1.sp
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
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
