package cx.aswin.boxcast.feature.home.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import cx.aswin.boxcast.feature.home.settings.components.SettingsContent
import cx.aswin.boxcast.feature.home.settings.components.SettingsGroup
import cx.aswin.boxcast.feature.home.settings.components.SettingsIconContainer
import cx.aswin.boxcast.feature.home.settings.components.SettingsScaffold

private const val GroqOnboardingModel = "openai/gpt-oss-120b"

@Composable
internal fun PrivacySettingsPage(
    deletionId: String,
    isDeletionExpanded: Boolean,
    onDeletionExpandedChange: (Boolean) -> Unit,
    onResetIdentityClick: () -> Unit,
    onCopyDeletionId: () -> Unit,
    onEmailDeletionRequest: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = "Privacy",
        onBack = onBack,
    ) {
        PromiseCard()

        SettingsGroup(title = "What this can look like — and what it isn’t") {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Analytics can suggest a rough sketch (rough place, device, podcast taste) — " +
                            "never a precise profile, and never age or gender. We don’t ask for name or " +
                            "email, and there’s no account; your library stays on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "The only personal details that show up are ones you type into search or " +
                            "AI chat. Please don’t.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "And again: never sold, never for ads. boxlore will never have ads.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        SettingsGroup(title = "1 · App use") {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Analytics,
                collected = "Screen opens, feature taps, settings changes, and rough time spent. " +
                    "Things like home carousel swipes, explore searches, library visits, " +
                    "settings opens, mini-player taps — and a few more of that kind.",
                reason = "So we can see how many people are actually using boxlore, which parts feel alive, " +
                    "and which ones flop — so we can make better product calls. " +
                    "And honestly, so we can watch the charts and feel happy that people are using something we built.",
                example = "Earlier builds had a Radio feature. Usage showed almost nobody used it, " +
                    "so we removed it instead of keeping dead weight in the app.",
            )
        }

        SettingsGroup(title = "2 · Search and onboarding text") {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Search,
                collected = "Search queries and what the app returns, plus AI onboarding chat text. " +
                    "AI onboarding runs on Groq using $GroqOnboardingModel.",
                reason = "Podcast Index and Apple’s APIs lean hard on exact-word matching. " +
                    "Search quality is one of the biggest pain points we’re trying to fix, " +
                    "so seeing real queries and responses is critical. " +
                    "Same for AI onboarding — we need to know whether prompts are understood " +
                    "and answers stay relevant.",
                example = "A lot of people typed real podcast names and treated the AI like a search box. " +
                    "The model kept asking generic taste questions instead of helping. " +
                    "So we added a layer that checks if the text is basically a show name, " +
                    "validates it against the chat context, and offers “use search instead” with that title " +
                    "so you can subscribe — while the AI still uses it as a taste baseline and the chat continues.",
            )
        }

        SettingsGroup(title = "3 · Listening activity") {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Podcasts,
                collected = "Podcast and episode details, playback progress, likes, subscriptions, downloads, " +
                    "and related listening signals.",
                reason = "Podcast Index charts aren’t great for this, and Apple charts don’t really give us " +
                    "the play-level signal we need. We use this to understand listening activity, " +
                    "and to build community charts that are native to boxlore.",
                example = null,
            )
        }

        SettingsGroup(title = "4 · App and device") {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Devices,
                collected = "App version, OS, device details, local hour, and your analytics ID.",
                reason = "We don’t really need this for product decisions — PostHog tracks it by default, " +
                    "and we don’t currently have a clean way to turn that part off.",
                example = null,
            )
        }

        SettingsGroup(title = "5 · Crashes") {
            PrivacyCategoryContent(
                icon = Icons.Rounded.BugReport,
                collected = "Technical errors and crash reports.",
                reason = "Pretty obvious — if the app breaks, we need to know so we can fix it.",
                example = null,
            )
        }

        SettingsGroup(
            title = "Your analytics ID",
            footer = "Analytics starts with the app and there’s no in-app opt-out. " +
                "These actions only touch PostHog — not your library, downloads, or data " +
                "sent for recommendations / transcripts.",
        ) {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnalyticsControlCard(
                        title = "Start a new analytics ID",
                        body = "Future events use a new ID. Anything already sent stays as-is.",
                        icon = Icons.Rounded.Refresh,
                        destructive = false,
                        actionLabel = "Reset ID",
                        onAction = onResetIdentityClick,
                        expansion = null,
                    )

                    AnalyticsControlCard(
                        title = "Request deletion of analytics data",
                        body = "Opens an email draft with your PostHog ID. You still have to hit send.",
                        icon = Icons.Rounded.DeleteForever,
                        destructive = true,
                        actionLabel = if (isDeletionExpanded) "Hide" else "Show ID",
                        onAction = { onDeletionExpandedChange(!isDeletionExpanded) },
                        expansion = AnalyticsCardExpansion(
                            expanded = isDeletionExpanded,
                            onToggleExpand = { onDeletionExpandedChange(!isDeletionExpanded) },
                            content = {
                                DeletionRequestPanel(
                                    deletionId = deletionId,
                                    onCopyDeletionId = onCopyDeletionId,
                                    onEmailDeletionRequest = onEmailDeletionRequest,
                                )
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromiseCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsIconContainer(
                    icon = Icons.Rounded.VolunteerActivism,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    size = 48.dp,
                    shape = MaterialTheme.shapes.large,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Our promise",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "From the people building boxlore",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }

            Text(
                text = "boxlore isn’t meant for monetary gain. It’s a few friends finishing a childhood dream — " +
                    "watching code turn into magic on screens.",
                style = MaterialTheme.typography.bodyLarge,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "What that means for your data",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                PromisePillRow(
                    pills = listOf(
                        "Never sold",
                        "Never for ads",
                        "No ads in boxlore",
                        "Usage only",
                    ),
                )
                Text(
                    text = "Whatever we collect will never be sold, never used to push ads, " +
                        "and never used for the other creepy things people do with data. " +
                        "It’s only to understand how many people use the app, how they use it, " +
                        "and which features work or don’t.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromisePillRow(pills: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pills.forEach { label ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PrivacyCategoryContent(
    icon: ImageVector,
    collected: String,
    reason: String,
    example: String?,
) {
    SettingsContent {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsIconContainer(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            PrivacyField(
                label = "What we collect",
                body = collected,
            )
            PrivacyField(
                label = "Why",
                body = reason,
            )

            if (example != null) {
                ExampleCallout(text = example)
            }
        }
    }
}

@Composable
private fun PrivacyField(
    label: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExampleCallout(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Example from real usage",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Expand/collapse affordance for [AnalyticsControlCard]; omitted entirely for non-expandable cards. */
private data class AnalyticsCardExpansion(
    val expanded: Boolean,
    val onToggleExpand: () -> Unit,
    val content: @Composable () -> Unit,
)

private data class AnalyticsCardColors(
    val container: Color,
    val onContainer: Color,
    val iconContainer: Color,
    val iconContent: Color,
)

@Composable
private fun analyticsCardColors(destructive: Boolean): AnalyticsCardColors = if (destructive) {
    AnalyticsCardColors(
        container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        onContainer = MaterialTheme.colorScheme.onErrorContainer,
        iconContainer = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
        iconContent = MaterialTheme.colorScheme.error,
    )
} else {
    AnalyticsCardColors(
        container = MaterialTheme.colorScheme.surface,
        onContainer = MaterialTheme.colorScheme.onSurface,
        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
        iconContent = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun AnalyticsCardActionButton(
    destructive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    if (destructive) {
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(actionLabel)
        }
    } else {
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun AnalyticsCardHeader(
    title: String,
    body: String,
    icon: ImageVector,
    colors: AnalyticsCardColors,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsIconContainer(
            icon = icon,
            containerColor = colors.iconContainer,
            contentColor = colors.iconContent,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onContainer.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun AnalyticsCardFooter(
    destructive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    expansion: AnalyticsCardExpansion?,
    onContainer: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expansion != null) {
            Icon(
                imageVector = if (expansion.expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = null,
                tint = onContainer.copy(alpha = 0.7f),
            )
        }
        AnalyticsCardActionButton(
            destructive = destructive,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }
}

@Composable
private fun AnalyticsControlCard(
    title: String,
    body: String,
    icon: ImageVector,
    destructive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    expansion: AnalyticsCardExpansion?,
) {
    val colors = analyticsCardColors(destructive)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnalyticsCardHeader(title = title, body = body, icon = icon, colors = colors)
            AnalyticsCardFooter(
                destructive = destructive,
                actionLabel = actionLabel,
                onAction = onAction,
                expansion = expansion,
                onContainer = colors.onContainer,
            )
            if (expansion != null) {
                AnimatedVisibility(visible = expansion.expanded) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        expansion.content()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletionRequestPanel(
    deletionId: String,
    onCopyDeletionId: () -> Unit,
    onEmailDeletionRequest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "The draft includes this PostHog ID. You can copy it if you want.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .expressiveClickable(
                    shape = MaterialTheme.shapes.small,
                    onClick = onCopyDeletionId,
                ),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = deletionId,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy PostHog ID",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onEmailDeletionRequest,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Email,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Open email draft",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
