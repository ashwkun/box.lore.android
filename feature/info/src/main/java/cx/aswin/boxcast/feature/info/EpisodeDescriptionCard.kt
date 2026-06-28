package cx.aswin.boxcast.feature.info

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.designsystem.component.HtmlText
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.core.model.Person

// --- Data Models ---

internal data class SocialLink(
    val platform: String,
    val url: String,
    val brandColor: Color,
    val icon: ImageVector
)



// --- URL Extraction & Categorization ---

private val HREF_REGEX = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val URL_REGEX = Regex("""https?://[^\s<>"')\]]+""")

// Known tracking/feed/infrastructure domains to skip entirely
private val SKIP_HOSTS = setOf(
    "podcastindex", "feeds.", "anchor.fm", "podtrac", "chartable", "feedburner",
    "podcasts.google.com"
)

private const val DISCORD_GG_HOST = "discord.gg"

private fun extractHandle(url: String, host: String): String? {
    return try {
        val uri = java.net.URI(url)
        val path = uri.path ?: return null
        val segments = path.split("/").filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        when {
            host.contains("youtube.com") || host.contains("youtu.be") -> extractYouTubeHandle(segments)
            host.contains("reddit.com") -> extractRedditHandle(segments)
            host.contains("discord.com") || host.contains(DISCORD_GG_HOST) -> extractDiscordHandle(host, segments)
            host.contains("instagram.com") ||
            host.contains("twitter.com") || host.contains("x.com") ||
            host.contains("threads.net") ||
            host.contains("patreon.com") ||
            host.contains("tiktok.com") ||
            host.contains("twitch.tv") ||
            host.contains("facebook.com") || host.contains("fb.com") -> extractGenericSocialHandle(segments)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun extractYouTubeHandle(segments: List<String>): String? {
    val first = segments.firstOrNull() ?: return null
    return when {
        first.startsWith("@") -> first
        first == "c" || first == "user" -> segments.getOrNull(1)?.let { "@$it" }
        first == "channel" -> null
        else -> if (first.length > 4 && !first.startsWith("UC")) "@$first" else null
    }
}

private fun extractRedditHandle(segments: List<String>): String? {
    if (segments.size < 2) return null
    val type = segments[0]
    val name = segments[1]
    return when (type) {
        "r" -> "r/$name"
        "u", "user" -> "u/$name"
        else -> null
    }
}

private fun extractDiscordHandle(host: String, segments: List<String>): String? {
    if (host.contains(DISCORD_GG_HOST)) return segments.firstOrNull()
    return if (segments.firstOrNull() == "invite") segments.getOrNull(1) else segments.firstOrNull()
}

private fun extractGenericSocialHandle(segments: List<String>): String? {
    val first = segments.firstOrNull() ?: return null
    val ignore = setOf(
        "share", "intent", "hashtag", "p", "reel", "stories", 
        "explore", "home", "tos", "privacy", "login", "signup",
        "messages", "notifications", "settings", "search", "about"
    )
    if (ignore.contains(first.lowercase()) || first.length < 2) return null
    return if (first.startsWith("@")) first else "@$first"
}

internal fun extractSocialLinks(html: String): List<SocialLink> {
    val urls = mutableSetOf<String>()
    HREF_REGEX.findAll(html).forEach { urls.add(it.groupValues[1]) }
    URL_REGEX.findAll(html).forEach { urls.add(it.value.trimEnd('.', ',', ';')) }

    return urls.mapNotNull { url ->
        val host = try {
            java.net.URI(url).host?.lowercase() ?: ""
        } catch (_: Exception) { "" }

        if (host.isEmpty() || SKIP_HOSTS.any { skip -> host.contains(skip) }) return@mapNotNull null

        val handle = extractHandle(url, host)
        buildSocialLinkFromHost(host, url, handle)
    }.distinctBy { it.url.lowercase().trim() }
}

private fun buildSocialLinkFromHost(host: String, url: String, handle: String?): SocialLink {
    return getMediaSocialLink(host, url, handle)
        ?: getCommunitySocialLink(host, url, handle)
        ?: buildGenericWebLink(host, url)
}

private fun getMediaSocialLink(host: String, url: String, handle: String?): SocialLink? {
    return when {
        host.contains("youtube.com") || host.contains("youtu.be") ->
            SocialLink(if (handle != null) "YouTube: $handle" else "YouTube", url, Color(0xFFFF0000), Icons.Rounded.PlayCircle)
        host.contains("instagram.com") ->
            SocialLink(if (handle != null) "Instagram: $handle" else "Instagram", url, Color(0xFFE4405F), Icons.Rounded.CameraAlt)
        host.contains("twitter.com") || host.contains("x.com") ->
            SocialLink(if (handle != null) "X: $handle" else "X", url, Color(0xFF1DA1F2), Icons.Rounded.Tag)
        host.contains("threads.net") ->
            SocialLink(if (handle != null) "Threads: $handle" else "Threads", url, Color(0xFF101010), Icons.Rounded.AlternateEmail)
        host.contains("spotify.com") || host.contains("open.spotify.com") ->
            SocialLink("Spotify", url, Color(0xFF1DB954), Icons.Rounded.MusicNote)
        host.contains("podcasts.apple.com") ->
            SocialLink("Apple Podcasts", url, Color(0xFF9933CC), Icons.Rounded.Podcasts)
        else -> null
    }
}

private fun getCommunitySocialLink(host: String, url: String, handle: String?): SocialLink? {
    return when {
        host.contains("patreon.com") ->
            SocialLink(if (handle != null) "Patreon: $handle" else "Patreon", url, Color(0xFFF96854), Icons.Rounded.Loyalty)
        host.contains("tiktok.com") ->
            SocialLink(if (handle != null) "TikTok: $handle" else "TikTok", url, Color(0xFFEE1D52), Icons.Rounded.Videocam)
        host.contains("facebook.com") || host.contains("fb.com") ->
            SocialLink(if (handle != null) "Facebook: $handle" else "Facebook", url, Color(0xFF1877F2), Icons.Rounded.People)
        host.contains("discord.com") || host.contains(DISCORD_GG_HOST) ->
            SocialLink(if (handle != null) "Discord: $handle" else "Discord", url, Color(0xFF5865F2), Icons.Rounded.Forum)
        host.contains("linkedin.com") ->
            SocialLink("LinkedIn", url, Color(0xFF0A66C2), Icons.Rounded.Work)
        host.contains("twitch.tv") ->
            SocialLink(if (handle != null) "Twitch: $handle" else "Twitch", url, Color(0xFF9146FF), Icons.Rounded.Videocam)
        host.contains("reddit.com") ->
            SocialLink(if (handle != null) "Reddit: $handle" else "Reddit", url, Color(0xFFFF4500), Icons.Rounded.Forum)
        else -> null
    }
}

private fun buildGenericWebLink(host: String, url: String): SocialLink {
    val name = host.removePrefix("www.").split(".").first()
        .replaceFirstChar { c -> c.uppercase() }
    return SocialLink(name, url, Color(0xFF607D8B), Icons.Rounded.Language)
}

// --- Composable ---

@Composable
internal fun EpisodeDescriptionCard(
    description: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    location: String? = null,
    license: String? = null,
    persons: List<Person>? = null,
    onSeekTo: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val socialLinks = remember(description) { extractSocialLinks(description) }
    var expanded by remember { mutableStateOf(false) }
    val isLong = remember(description) { description.length > 500 }
    val formattedDescription = remember(description) {
        formatTimestampsAsLinks(description)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .expressiveClickable(enabled = isLong) { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
        ) {
            // --- Cast & Crew (Person chips) ---
            if (!persons.isNullOrEmpty()) {
                Text(
                    text = "Cast & Crew",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(persons) { person ->
                        PersonChip(
                            person = person,
                            onClick = {
                                if (!person.href.isNullOrBlank()) {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(person.href))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Social Links ---
            if (socialLinks.isNotEmpty()) {
                Text(
                    text = "Episode Resources",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- Description ---
            Text(
                text = "About this episode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                HtmlText(
                    text = formattedDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded || !isLong) Int.MAX_VALUE else 4,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { if (isLong) expanded = !expanded },
                    onLinkClicked = { url ->
                        if (url.startsWith("play-position:")) {
                            val seconds = url.substringAfter("play-position:").toLongOrNull() ?: 0L
                            onSeekTo?.invoke(seconds * 1000L)
                            true
                        } else {
                            false
                        }
                    }
                )

                if (!expanded && isLong) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                )
                            )
                    )
                }
            }

            if (isLong) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveClickable(shape = RoundedCornerShape(8.dp)) { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) "Show less" else "Read more",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accentColor.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = accentColor.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }



            // --- Metadata Footer (Location & License) ---
            if (!location.isNullOrBlank() || !license.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!location.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = "Location",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = location,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    if (!license.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Copyright,
                                contentDescription = "License",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatLicense(license),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestampsAsLinks(htmlText: String): String {
    if (htmlText.isBlank()) return ""
    val regex = """\b(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\b""".toRegex()
    return regex.replace(htmlText) { match ->
        val hours = match.groups[1]?.value?.toIntOrNull() ?: 0
        val minutes = match.groups[2]?.value?.toIntOrNull() ?: 0
        val seconds = match.groups[3]?.value?.toIntOrNull() ?: 0
        
        if (minutes < 60 && seconds < 60) {
            val totalSeconds = hours * 3600 + minutes * 60 + seconds
            "<a href=\"play-position:$totalSeconds\">▶ ${match.value}</a>"
        } else {
            match.value
        }
    }
}

internal fun formatLicense(licenseCode: String): String {
    val clean = licenseCode.trim().lowercase()
    if (clean.startsWith("cc-") || clean == "cc0" || clean.contains("creative-commons") || clean.contains("creative commons")) {
        val suffix = clean.removePrefix("cc-").removePrefix("creative-commons-").removePrefix("creative commons-").uppercase()
        return when (suffix) {
            "0", "CC0", "ZERO" -> "Public Domain (CC0)"
            "BY" -> "Creative Commons BY"
            "BY-SA" -> "Creative Commons BY-SA"
            "BY-NC" -> "Creative Commons BY-NC"
            "BY-ND" -> "Creative Commons BY-ND"
            "BY-NC-SA" -> "Creative Commons BY-NC-SA"
            "BY-NC-ND" -> "Creative Commons BY-NC-ND"
            else -> "Creative Commons ${suffix.ifEmpty { "License" }}"
        }
    }
    return when (clean) {
        "all-rights-reserved", "copyright" -> "All Rights Reserved"
        "public-domain" -> "Public Domain"
        else -> licenseCode.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

// --- Person Chip (Premium avatar + name + role) ---

@Composable
internal fun PersonChip(
    person: Person,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = ExpressiveShapes.Pill,
        modifier = Modifier.expressiveClickable(
            enabled = !person.href.isNullOrBlank(),
            isolate = true,
            onClick = onClick
        )
    ) {
        Row(
            modifier = Modifier.padding(
                start = 4.dp,
                end = 14.dp,
                top = 4.dp,
                bottom = 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Avatar
            if (!person.img.isNullOrBlank()) {
                OptimizedImage(
                    url = person.img,
                    proxyWidth = 80, // 32dp * ~2.5x density
                    contentDescription = person.name,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback avatar with initial
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Name + Role
            Column {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!person.role.isNullOrBlank()) {
                    val roleText = person.role!!
                    Text(
                        text = roleText.replaceFirstChar { c -> c.uppercaseChar() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
        shape = ExpressiveShapes.Pill,
        modifier = Modifier
            .expressiveClickable(isolate = true, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = link.icon,
                contentDescription = link.platform,
                tint = link.brandColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = link.platform,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = link.brandColor
            )
        }
    }
}


