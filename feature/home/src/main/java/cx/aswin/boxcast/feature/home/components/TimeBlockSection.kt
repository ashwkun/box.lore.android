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
import cx.aswin.boxcast.core.data.analytics.AnalyticsHelper
import cx.aswin.boxcast.core.data.privacy.ConsentManager

@Composable
fun TimeBlockSection(
    data: CuratedTimeBlock,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val analyticsHelper = androidx.compose.runtime.remember {
        AnalyticsHelper(context, ConsentManager(context))
    }

    // Track block impression once when this composable enters composition
    LaunchedEffect(data.title) {
        val totalPods = data.sections.sumOf { it.podcasts.size }
        analyticsHelper.logCuratedBlockImpression(data.title, data.sections.size, totalPods)
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // --- Master Header ---
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = data.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = SectionHeaderFontFamily,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = data.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

            // --- Genre Rails ---
            data.sections.forEachIndexed { index, section ->
                // Track per-vibe impression
                LaunchedEffect(section.category) {
                    analyticsHelper.logCuratedVibeImpression(section.category, section.podcasts.size)
                }

                Column {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

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
                                        // Track curated card tap + episode play
                                        analyticsHelper.logCuratedCardTapped(section.category, podcast.id, i)
                                        analyticsHelper.logCuratedEpisodePlayed(section.category, podcast.id, i)
                                        onEpisodeClick(episode, podcast)
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (index < data.sections.size - 1) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
    }
}
