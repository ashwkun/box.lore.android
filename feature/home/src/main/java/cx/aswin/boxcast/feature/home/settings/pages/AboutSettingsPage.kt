package cx.aswin.boxcast.feature.home.settings.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.R
import cx.aswin.boxcast.feature.home.settings.components.SettingsContent
import cx.aswin.boxcast.feature.home.settings.components.SettingsGroup
import cx.aswin.boxcast.feature.home.settings.components.SettingsNavigationRow
import cx.aswin.boxcast.feature.home.settings.components.SettingsScaffold

@Composable
internal fun AboutSettingsPage(
    versionName: String,
    versionCode: Long,
    packageName: String,
    androidRelease: String,
    sdkInt: Int,
    onVisitPodcastIndex: () -> Unit,
    onOpenChangelog: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = "About boxlore",
        onBack = onBack,
    ) {
        SettingsGroup(title = "Catalog") {
            SettingsContent {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Powered by Podcast Index",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "boxlore’s podcast catalog comes from Podcast Index — " +
                            "an open directory that makes indie podcast discovery possible. " +
                            "We’re grateful they exist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_podcast_index_logo),
                            contentDescription = "Podcast Index logo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 24.dp)
                                .height(72.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Button(
                        onClick = onVisitPodcastIndex,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Launch,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Visit podcastindex.org")
                    }
                }
            }
        }

        SettingsGroup(title = "App") {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    SpecRow(label = "Version", value = versionName)
                    SpecDivider()
                    SpecRow(label = "Build", value = versionCode.toString())
                    SpecDivider()
                    SpecRow(label = "Package", value = packageName, monospace = true)
                    SpecDivider()
                    SpecRow(label = "Android", value = "$androidRelease (API $sdkInt)")
                }
            }
            SettingsNavigationRow(
                title = "Changelog",
                supportingText = "What’s new on GitHub",
                icon = Icons.Rounded.HistoryEdu,
                onClick = onOpenChangelog,
            )
        }
    }
}

@Composable
private fun SpecRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.65f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpecDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}
