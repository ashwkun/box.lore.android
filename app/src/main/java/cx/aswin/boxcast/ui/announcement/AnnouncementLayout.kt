package cx.aswin.boxcast.ui.announcement

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual layout for the in-app announcement dialog, resolved from the FCM `category` badge.
 * Wire format is unchanged — only known badge strings map to specialized layouts.
 */
enum class AnnouncementLayout {
    WhatsNew,
    NewFeature,
    Tip,
    Important,
    General,
}

data class AnnouncementLayoutStyle(
    val layout: AnnouncementLayout,
    val icon: ImageVector,
    val imageHeight: Dp,
    val emphasizeImage: Boolean,
    val emphasizeBullets: Boolean,
    val useErrorChip: Boolean,
    val useTertiaryChip: Boolean,
)

fun resolveAnnouncementLayout(category: String): AnnouncementLayout {
    val c = category.trim().uppercase().replace('’', '\'')
    return when {
        c == "WHAT'S NEW" || c == "WHATS NEW" || c == "NEW RELEASE" -> AnnouncementLayout.WhatsNew
        c == "NEW FEATURE" -> AnnouncementLayout.NewFeature
        c == "TIP" -> AnnouncementLayout.Tip
        c == "IMPORTANT" || c == "ALERT" -> AnnouncementLayout.Important
        else -> AnnouncementLayout.General
    }
}

fun announcementLayoutStyle(category: String): AnnouncementLayoutStyle {
    return when (resolveAnnouncementLayout(category)) {
        AnnouncementLayout.WhatsNew ->
            AnnouncementLayoutStyle(
                layout = AnnouncementLayout.WhatsNew,
                icon = Icons.Rounded.NewReleases,
                imageHeight = 140.dp,
                emphasizeImage = false,
                emphasizeBullets = true,
                useErrorChip = false,
                useTertiaryChip = false,
            )
        AnnouncementLayout.NewFeature ->
            AnnouncementLayoutStyle(
                layout = AnnouncementLayout.NewFeature,
                icon = Icons.Rounded.Star,
                imageHeight = 200.dp,
                emphasizeImage = true,
                emphasizeBullets = false,
                useErrorChip = false,
                useTertiaryChip = false,
            )
        AnnouncementLayout.Tip ->
            AnnouncementLayoutStyle(
                layout = AnnouncementLayout.Tip,
                icon = Icons.Rounded.Lightbulb,
                imageHeight = 140.dp,
                emphasizeImage = false,
                emphasizeBullets = false,
                useErrorChip = false,
                useTertiaryChip = true,
            )
        AnnouncementLayout.Important ->
            AnnouncementLayoutStyle(
                layout = AnnouncementLayout.Important,
                icon = Icons.Rounded.Warning,
                imageHeight = 140.dp,
                emphasizeImage = false,
                emphasizeBullets = false,
                useErrorChip = true,
                useTertiaryChip = false,
            )
        AnnouncementLayout.General ->
            AnnouncementLayoutStyle(
                layout = AnnouncementLayout.General,
                icon = Icons.Rounded.Campaign,
                imageHeight = 168.dp,
                emphasizeImage = false,
                emphasizeBullets = false,
                useErrorChip = false,
                useTertiaryChip = false,
            )
    }
}
