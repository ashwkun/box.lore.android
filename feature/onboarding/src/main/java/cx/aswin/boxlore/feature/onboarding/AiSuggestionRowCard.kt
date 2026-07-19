package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast

/** Stable Compose [testTag] ids for onboarding suggestion instrumentation / Maestro. */
internal object SuggestedPodcastTestTags {
    const val TOGGLE = "onboarding_subscribe_toggle"
}

@Composable
internal fun SuggestedPodcastRowItem(
    podcast: Podcast,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val cardStyle = suggestedPodcastCardStyle(isSubscribed)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardStyle.background),
        border = cardStyle.border,
        modifier =
            modifier
                .fillMaxWidth()
                .expressiveClickable { expanded = !expanded },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SuggestedPodcastCover(podcast)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                SuggestedPodcastGenre(genre = podcast.genre)
                SuggestedPodcastTitleAndArtist(podcast = podcast, isSubscribed = isSubscribed)
                ExpandablePodcastDescription(
                    podcast = podcast,
                    expanded = expanded,
                    style =
                        PodcastDescriptionStyle(
                            bodyStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp, fontSize = 12.sp),
                            readMoreFontSize = 10.sp,
                            readMoreTopPadding = 2.dp,
                            alpha = 0.8f,
                        ),
                    collapsedLines = 2,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            SuggestedPodcastToggle(
                podcastId = podcast.id,
                isSubscribed = isSubscribed,
                onToggleSubscription = onToggleSubscription,
            )
        }
    }
}

@Composable
private fun suggestedPodcastCardStyle(isSubscribed: Boolean): PodcastCardStyle =
    if (isSubscribed) {
        PodcastCardStyle(
            background =
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.05f)
                    .compositeOver(MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        )
    } else {
        PodcastCardStyle(
            background = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        )
    }

@Composable
private fun SuggestedPodcastCover(podcast: Podcast) {
    Box(
        modifier =
            Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp)),
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            proxyWidth = 160,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SuggestedPodcastGenre(genre: String) {
    if (genre.isBlank() || genre == "Podcast") return
    Box(
        modifier =
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp),
                ).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = genre.uppercase(),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SuggestedPodcastTitleAndArtist(
    podcast: Podcast,
    isSubscribed: Boolean,
) {
    Text(
        text = podcast.title,
        style =
            MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
            ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = podcast.artist,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SuggestedPodcastToggle(
    podcastId: String,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .padding(top = 4.dp)
                .size(40.dp)
                .testTag(SuggestedPodcastTestTags.TOGGLE)
                .expressiveClickable(shape = androidx.compose.foundation.shape.CircleShape) {
                    onToggleSubscription(podcastId)
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .background(
                        color = if (isSubscribed) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ).border(
                        width = 2.dp,
                        color = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSubscribed) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
