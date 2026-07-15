package cx.aswin.boxcast.feature.home.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RssFeed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.RegionSegmentedSelector
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.feature.home.settings.components.SettingsContent
import cx.aswin.boxcast.feature.home.settings.components.SettingsDivider
import cx.aswin.boxcast.feature.home.settings.components.SettingsGroup
import cx.aswin.boxcast.feature.home.settings.components.SettingsNavigationRow
import cx.aswin.boxcast.feature.home.settings.components.SettingsScaffold

/**
 * Export/import callbacks for [LibrarySettingsPage], grouped to keep its parameter count small.
 * Also used by [cx.aswin.boxcast.feature.home.settings.SettingsScreen].
 */
data class LibraryBackupActions(
    val onExportJson: () -> Unit,
    val onExportOpml: () -> Unit,
    val onImportJson: () -> Unit,
    val onImportOpml: () -> Unit,
)

@Composable
internal fun LibrarySettingsPage(
    currentRegion: String,
    onSetRegion: (String) -> Unit,
    onAddRssClick: () -> Unit,
    backupActions: LibraryBackupActions,
    onBack: () -> Unit,
) {
    var isCountryFaqExpanded by rememberSaveable { mutableStateOf(false) }
    val collapseCountryFaq = { isCountryFaqExpanded = false }

    SettingsScaffold(
        title = "Library",
        onBack = onBack,
        onUnconsumedTap = if (isCountryFaqExpanded) collapseCountryFaq else null,
    ) {
        SettingsGroup(title = "Content region") {
            SettingsContent {
                RegionSegmentedSelector(
                    activeRegion = currentRegion,
                    onSwitchRegion = {
                        collapseCountryFaq()
                        onSetRegion(it)
                    },
                )
            }
            SettingsDivider()
            CountryNotListedFaq(
                expanded = isCountryFaqExpanded,
                onExpand = { isCountryFaqExpanded = true },
                onCollapse = collapseCountryFaq,
            )
        }

        SettingsGroup(title = "RSS") {
            SettingsNavigationRow(
                title = "Add podcast by RSS feed",
                supportingText = "For podcasts that are not in the catalog",
                icon = Icons.Rounded.RssFeed,
                onClick = {
                    collapseCountryFaq()
                    onAddRssClick()
                },
            )
        }

        SettingsGroup(title = "Export") {
            SettingsNavigationRow(
                title = "Full library backup",
                supportingText = "Subscriptions, history, likes, and settings (JSON)",
                icon = Icons.Rounded.FileUpload,
                onClick = {
                    collapseCountryFaq()
                    backupActions.onExportJson()
                },
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = "Subscriptions only",
                supportingText = "OPML file",
                icon = Icons.Rounded.FileUpload,
                onClick = {
                    collapseCountryFaq()
                    backupActions.onExportOpml()
                },
            )
        }

        SettingsGroup(title = "Import") {
            SettingsNavigationRow(
                title = "Full library backup",
                supportingText = "Restore from a JSON backup",
                icon = Icons.Rounded.FileDownload,
                onClick = {
                    collapseCountryFaq()
                    backupActions.onImportJson()
                },
            )
            SettingsDivider()
            SettingsNavigationRow(
                title = "Subscriptions only",
                supportingText = "Import from an OPML file",
                icon = Icons.Rounded.FileDownload,
                onClick = {
                    collapseCountryFaq()
                    backupActions.onImportOpml()
                },
            )
        }
    }
}

@Composable
private fun CountryNotListedFaq(
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .expressiveClickable(
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        if (expanded) onCollapse() else onExpand()
                    },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Why is my country not listed?",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveClickable(
                        shape = MaterialTheme.shapes.medium,
                        onClick = onCollapse,
                    )
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Why isn't my country listed?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "We are actively working to expand our global coverage! Currently, we prioritize adding new countries based on active user demand.",
                )
                Text(
                    text = "How this affects your experience:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "Search is still global: You can still search for, find, and access all content, even if your country isn't officially listed yet.",
                )
                FaqParagraph(
                    "Smart recommendations still work: The recommendation engine will still suggest the closest matches based on your listening habits, irrespective of your country selection.",
                )
                FaqParagraph(
                    "What you'll miss (for now): Country selection primarily fine-tunes your charts and local language filters. For example, selecting France prioritizes French + English content, while selecting India prioritizes Hindi + English.",
                )
                Text(
                    text = "Want your country added next?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FaqParagraph(
                    "Help us prioritize your region! Please submit a country or language support request via our Feedback module or GitHub.",
                )
            }
        }
    }
}

@Composable
private fun FaqParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
