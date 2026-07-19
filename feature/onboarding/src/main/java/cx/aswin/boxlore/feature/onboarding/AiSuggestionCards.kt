package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast

@Composable
internal fun HeroPodcastCard(
    podcast: Podcast,
    categoryName: String,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val cardStyle = heroPodcastCardStyle(isSubscribed)
    val cardModifier = heroPodcastCardModifier(modifier, expanded) { expanded = !expanded }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardStyle.background),
        border = cardStyle.border,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = cardModifier,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .then(if (expanded) Modifier.wrapContentHeight() else Modifier.fillMaxHeight()),
        ) {
            HeroPodcastCover(podcast)

            Spacer(modifier = Modifier.height(12.dp))

            HeroPodcastBadges(categoryName)
            PodcastTitleAndArtist(podcast)
            ExpandablePodcastDescription(
                podcast = podcast,
                expanded = expanded,
                style =
                    PodcastDescriptionStyle(
                        bodyStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp, fontSize = 13.sp),
                        readMoreFontSize = MaterialTheme.typography.labelSmall.fontSize,
                        readMoreTopPadding = 4.dp,
                        alpha = 0.9f,
                    ),
                collapsedLines = 2,
                modifier = Modifier.padding(top = 10.dp),
            )
            HeroPodcastBottomSpacer(expanded)
            PodcastSubscriptionButton(
                podcastId = podcast.id,
                isSubscribed = isSubscribed,
                onToggleSubscription = onToggleSubscription,
                selectedText = "Selected",
                unselectedText = "Select Show",
                modifier = Modifier.height(48.dp),
            )
        }
    }
}

internal data class PodcastCardStyle(
    val background: Color,
    val border: BorderStroke,
)

internal data class PodcastDescriptionStyle(
    val bodyStyle: TextStyle,
    val readMoreFontSize: TextUnit,
    val readMoreTopPadding: Dp,
    val alpha: Float,
)

@Composable
private fun heroPodcastCardStyle(isSubscribed: Boolean): PodcastCardStyle =
    if (isSubscribed) {
        PodcastCardStyle(
            background =
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        )
    } else {
        PodcastCardStyle(
            background =
                MaterialTheme.colorScheme.surfaceVariant
                    .copy(alpha = 0.25f)
                    .compositeOver(MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )
    }

private fun heroPodcastCardModifier(
    modifier: Modifier,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
): Modifier {
    val clickableModifier = modifier.expressiveClickable(shape = RoundedCornerShape(24.dp), onClick = onToggleExpanded)
    return if (expanded) clickableModifier else clickableModifier.height(390.dp)
}

@Composable
private fun HeroPodcastCover(podcast: Podcast) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .size(120.dp)
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            proxyWidth = 240,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HeroPodcastBadges(categoryName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PodcastBadge(
            text = "AI TOP PICK",
            background = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        PodcastBadge(
            text = categoryName.uppercase(),
            background = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun PodcastBadge(
    text: String,
    background: Color,
    contentColor: Color,
    fontWeight: FontWeight,
    letterSpacing: androidx.compose.ui.unit.TextUnit,
) {
    Box(
        modifier =
            Modifier
                .background(color = background, shape = RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontWeight = fontWeight,
                    letterSpacing = letterSpacing,
                ),
        )
    }
}

@Composable
private fun PodcastTitleAndArtist(podcast: Podcast) {
    Text(
        text = podcast.title,
        style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 20.sp,
            ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        text = podcast.artist,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
internal fun ExpandablePodcastDescription(
    podcast: Podcast,
    expanded: Boolean,
    style: PodcastDescriptionStyle,
    collapsedLines: Int,
    modifier: Modifier = Modifier,
) {
    val description =
        remember(podcast.id, podcast.description, podcast.title, podcast.artist) {
            podcastDescription(podcast)
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        Text(
            text = description,
            style = style.bodyStyle,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = style.alpha),
        )
        Text(
            text = if (expanded) "Show less" else "Read more",
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = style.readMoreFontSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
            modifier = Modifier.padding(top = style.readMoreTopPadding),
        )
    }
}

private fun podcastDescription(podcast: Podcast): String {
    val rawDescription = podcast.description?.stripHtml()
    return if (!rawDescription.isNullOrBlank()) {
        rawDescription
    } else {
        "Explore episodes, discussions, and topics from ${podcast.title} by ${podcast.artist}."
    }
}

@Composable
private fun ColumnScope.HeroPodcastBottomSpacer(expanded: Boolean) {
    if (expanded) {
        Spacer(modifier = Modifier.height(16.dp))
    } else {
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun PodcastSubscriptionButton(
    podcastId: String,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
    selectedText: String,
    unselectedText: String,
    modifier: Modifier = Modifier,
) {
    val buttonStyle = subscriptionButtonStyle(isSubscribed, selectedText, unselectedText)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(buttonStyle.background, RoundedCornerShape(24.dp))
                .expressiveClickable(shape = RoundedCornerShape(24.dp)) {
                    onToggleSubscription(podcastId)
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = buttonStyle.icon,
            contentDescription = null,
            tint = buttonStyle.contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = buttonStyle.text,
            color = buttonStyle.contentColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private data class SubscriptionButtonStyle(
    val background: Color,
    val contentColor: Color,
    val text: String,
    val icon: ImageVector,
)

@Composable
private fun subscriptionButtonStyle(
    isSubscribed: Boolean,
    selectedText: String,
    unselectedText: String,
): SubscriptionButtonStyle =
    if (isSubscribed) {
        SubscriptionButtonStyle(
            background = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            text = selectedText,
            icon = Icons.Rounded.CheckCircle,
        )
    } else {
        SubscriptionButtonStyle(
            background = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            text = unselectedText,
            icon = Icons.Rounded.Add,
        )
    }

internal fun String.stripHtml(): String {
    val withoutTags = this.replace(Regex("<[^>]*>"), "")
    return withoutTags
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#8217;", "'")
        .replace("&#8216;", "'")
        .replace("&#8220;", "\"")
        .replace("&#8221;", "\"")
        .replace("&nbsp;", " ")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .trim()
}
