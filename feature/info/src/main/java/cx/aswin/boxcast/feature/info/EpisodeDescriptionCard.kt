package cx.aswin.boxcast.feature.info

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.designsystem.component.HtmlText
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable

// --- Data Model ---

internal data class SocialLink(
    val platform: String,
    val url: String,
    val brandColor: Color,
    val icon: ImageVector
)

// --- URL Extraction & Categorization ---

private val HREF_REGEX = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val URL_REGEX = Regex("""https?://[^\s<>"')\]]+""")

internal fun extractSocialLinks(html: String): List<SocialLink> {
    val urls = mutableSetOf<String>()

    HREF_REGEX.findAll(html).forEach { urls.add(it.groupValues[1]) }
    URL_REGEX.findAll(html).forEach { urls.add(it.value.trimEnd('.', ',', ';')) }

    return urls.mapNotNull { url ->
        val host = try {
            java.net.URI(url).host?.lowercase() ?: ""
        } catch (_: Exception) { "" }

        when {
            host.contains("youtube.com") || host.contains("youtu.be") ->
                SocialLink("YouTube", url, Color(0xFFFF0000), Icons.Rounded.PlayCircle)
            host.contains("instagram.com") ->
                SocialLink("Instagram", url, Color(0xFFE4405F), Icons.Rounded.CameraAlt)
            host.contains("twitter.com") || host.contains("x.com") ->
                SocialLink("X", url, Color(0xFF000000), Icons.Rounded.Tag)
            host.contains("spotify.com") || host.contains("open.spotify.com") ->
                SocialLink("Spotify", url, Color(0xFF1DB954), Icons.Rounded.MusicNote)
            host.contains("podcasts.apple.com") ->
                SocialLink("Apple Podcasts", url, Color(0xFF9933CC), Icons.Rounded.Podcasts)
            host.contains("patreon.com") ->
                SocialLink("Patreon", url, Color(0xFFF96854), Icons.Rounded.Loyalty)
            host.contains("tiktok.com") ->
                SocialLink("TikTok", url, Color(0xFF010101), Icons.Rounded.Videocam)
            host.contains("facebook.com") || host.contains("fb.com") ->
                SocialLink("Facebook", url, Color(0xFF1877F2), Icons.Rounded.People)
            host.contains("discord.com") || host.contains("discord.gg") ->
                SocialLink("Discord", url, Color(0xFF5865F2), Icons.Rounded.Forum)
            host.contains("linkedin.com") ->
                SocialLink("LinkedIn", url, Color(0xFF0A66C2), Icons.Rounded.Work)
            host.contains("twitch.tv") ->
                SocialLink("Twitch", url, Color(0xFF9146FF), Icons.Rounded.Videocam)
            host.contains("reddit.com") ->
                SocialLink("Reddit", url, Color(0xFFFF4500), Icons.Rounded.Forum)
            // Skip feed/API/tracking URLs
            host.contains("podcastindex") || host.contains("feeds.") ||
                host.contains("anchor.fm") || host.contains("podtrac") ||
                host.contains("chartable") || host.contains("feedburner") ||
                host.isEmpty() -> null
            // Generic website
            else -> {
                val name = host.removePrefix("www.").split(".").first()
                    .replaceFirstChar { it.uppercase() }
                SocialLink(name, url, Color(0xFF607D8B), Icons.Rounded.Language)
            }
        }
    }.distinctBy { it.platform }
}

// --- Composable ---

@Composable
internal fun EpisodeDescriptionCard(
    description: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val socialLinks = remember(description) { extractSocialLinks(description) }
    var expanded by remember { mutableStateOf(false) }
    val isLong = remember(description) { description.length > 200 }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 16.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            // --- Social Links Row ---
            if (socialLinks.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(socialLinks) { link ->
                        SocialChip(
                            link = link,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Subtle divider
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Description Text ---
            HtmlText(
                text = description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 24.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded || !isLong) Int.MAX_VALUE else 5,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // --- Expand/Collapse Toggle ---
            if (isLong) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveClickable { expanded = !expanded }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// --- Social Chip ---

@Composable
private fun SocialChip(
    link: SocialLink,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes.Pill,
        modifier = Modifier
            .expressiveClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = link.icon,
                contentDescription = link.platform,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = link.platform,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
