package cx.aswin.boxlore.ui.libraryimport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cx.aswin.boxlore.core.analytics.AnalyticsHelper

private val ImportCorner = RoundedCornerShape(24.dp)

internal fun contentKeyFor(state: OpmlImportState): String =
    when (state) {
        OpmlImportState.Idle -> "idle"
        OpmlImportState.ShowSelector -> "selector"
        OpmlImportState.ImportingJson -> "importing_json"
        is OpmlImportState.Parsing -> "parsing"
        is OpmlImportState.Importing -> "importing"
        is OpmlImportState.AskCompleted -> "ask"
        is OpmlImportState.Completing -> "completing"
        is OpmlImportState.Success -> "success"
        is OpmlImportState.Error -> "error"
    }

internal fun heroVisualFor(state: OpmlImportState): ImportHeroVisual? =
    when (state) {
        OpmlImportState.ImportingJson,
        is OpmlImportState.Parsing,
        -> ImportHeroVisual.Indeterminate
        is OpmlImportState.Importing -> ImportHeroVisual.Progress(state.progress.coerceIn(0f, 1f))
        is OpmlImportState.Completing -> ImportHeroVisual.Progress(state.progress.coerceIn(0f, 1f))
        is OpmlImportState.Success -> ImportHeroVisual.Complete
        else -> null
    }

@Composable
internal fun ProgressFlowScaffold(
    hero: ImportHeroVisual,
    state: OpmlImportState,
    onDone: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ImportStatusHero(visual = hero)
        Spacer(modifier = Modifier.height(28.dp))

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(initialScale = 0.98f, animationSpec = tween(240))) togetherWith
                    fadeOut(tween(160))
            },
            contentKey = { contentKeyFor(it) },
            label = "import_progress_copy",
        ) { current ->
            when (current) {
                OpmlImportState.ImportingJson ->
                    ProgressCopy(
                        title = "Restoring backup",
                        subtitle = "Bringing in shows and playback history",
                    )

                is OpmlImportState.Parsing ->
                    ProgressCopy(
                        title = "Reading OPML",
                        subtitle = "Finding podcast feeds in your file",
                    )

                is OpmlImportState.Importing ->
                    ProgressCopy(
                        title = "Importing podcasts",
                        subtitle = "Subscribing ${current.currentCount + 1} of ${current.totalCount}",
                        detail = current.currentFeedTitle,
                    )

                is OpmlImportState.Completing ->
                    ProgressCopy(
                        title = "Marking history played",
                        subtitle = "Catching up past episodes on selected shows",
                        detail = current.currentShowTitle,
                    )

                is OpmlImportState.Success ->
                    SuccessCopy(
                        state = current,
                        onDone = onDone,
                    )

                else -> Unit
            }
        }
    }
}

@Composable
internal fun ProgressCopy(
    title: String,
    subtitle: String,
    detail: String? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (!detail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(28.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ImportCorner,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
            ) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                )
            }
        }
    }
}

@Composable
internal fun SelectorContent(
    onJson: () -> Unit,
    onOpml: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(76.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.LibraryBooks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Import library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Restore a boxlore backup, or migrate shows from another podcast app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(modifier = Modifier.height(36.dp))

        ImportOptionCard(
            icon = Icons.Rounded.SettingsBackupRestore,
            title = "boxlore backup",
            subtitle = "JSON restore with subscriptions, likes, and playback history",
            badge = ".json",
            onClick = onJson,
        )
        Spacer(modifier = Modifier.height(12.dp))
        ImportOptionCard(
            icon = Icons.Rounded.ImportExport,
            title = "Other app export",
            subtitle = "OPML from Apple Podcasts, Spotify, Pocket Casts, and more",
            badge = ".opml",
            onClick = onOpml,
        )
    }
}

@Composable
internal fun ImportOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ImportCorner,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun hasPostNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
internal fun SuccessCopy(
    state: OpmlImportState.Success,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val showNotificationPrompt =
        state.isJson &&
            state.hasNotificationsEnabled &&
            !hasPostNotificationPermission(context)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "You're all set",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = successSubtitle(state.isJson),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))
        ImportSuccessSummary(state = state)

        if (showNotificationPrompt) {
            Spacer(modifier = Modifier.height(16.dp))
            ImportNotificationPermissionCard()
        }

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

internal fun successSubtitle(isJson: Boolean): String =
    if (isJson) {
        "Your backup is restored and ready to listen."
    } else {
        "Your shows are imported and ready to listen."
    }

@Composable
internal fun ImportSuccessSummary(state: OpmlImportState.Success) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ImportCorner,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SummaryRow(label = "Imported shows", value = "${state.importedCount}")
            if (!state.isJson && state.completedCount > 0) {
                SummaryRow(label = "Marked played", value = "${state.completedCount}")
            }
        }
    }
}

@Composable
internal fun ImportNotificationPermissionCard() {
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted ->
                AnalyticsHelper.trackNotificationPermissionDecided(granted)
            },
        )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ImportCorner,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.28f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enable notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text =
                    "This backup includes shows with alerts or auto-downloads. " +
                        "Allow notifications so they can keep working in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Grant permission", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

@Composable
internal fun SummaryRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun ErrorContent(
    message: String,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ImportStatusHero(visual = ImportHeroVisual.Error)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Import failed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ImportCorner,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onClose,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = "Close",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
