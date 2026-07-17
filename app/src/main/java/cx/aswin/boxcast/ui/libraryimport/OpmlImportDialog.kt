package cx.aswin.boxcast.ui.libraryimport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import cx.aswin.boxcast.core.data.analytics.AnalyticsHelper
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.delay

sealed interface OpmlImportState {
    data object Idle : OpmlImportState
    data object ShowSelector : OpmlImportState
    data object ImportingJson : OpmlImportState
    data class Parsing(val uri: android.net.Uri) : OpmlImportState
    data class Importing(
        val currentFeedTitle: String,
        val progress: Float,
        val currentCount: Int,
        val totalCount: Int,
        val importedPodcasts: List<Podcast>
    ) : OpmlImportState

    data class AskCompleted(
        val importedPodcasts: List<Podcast>,
        val selectedIds: Set<String>
    ) : OpmlImportState

    data class Completing(
        val progress: Float,
        val currentShowTitle: String,
        val podcastsToMark: List<Podcast>,
        val totalImportedCount: Int,
        val importedPodcasts: List<Podcast> = emptyList()
    ) : OpmlImportState

    data class Success(
        val importedCount: Int,
        val completedCount: Int,
        val isJson: Boolean = false,
        val importedPodcasts: List<Podcast> = emptyList(),
        val hasNotificationsEnabled: Boolean = false
    ) : OpmlImportState

    data class Error(val message: String) : OpmlImportState
}

private val ImportHeroSize = 80.dp
private val ImportCorner = RoundedCornerShape(24.dp)

private fun canDismissImportState(state: OpmlImportState): Boolean = when (state) {
    is OpmlImportState.ShowSelector,
    is OpmlImportState.AskCompleted,
    is OpmlImportState.Success,
    is OpmlImportState.Error -> true
    else -> false
}

@Composable
fun OpmlImportDialog(
    state: OpmlImportState,
    onDismissRequest: () -> Unit,
    onSelectionChanged: (selectedIds: Set<String>) -> Unit,
    onConfirmCompleted: () -> Unit,
    onSkipCompleted: () -> Unit,
    onImportJsonSelected: (android.net.Uri) -> Unit,
    onImportOpmlSelected: (android.net.Uri) -> Unit
) {
    if (state is OpmlImportState.Idle) return

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportJsonSelected(it) } }
    )
    val importOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let { onImportOpmlSelected(it) } }
    )
    val canDismiss = canDismissImportState(state)

    Dialog(
        onDismissRequest = {
            if (canDismiss) onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    if (canDismiss) {
                        ImportCloseButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                    ImportDialogBody(
                        state = state,
                        onDismissRequest = onDismissRequest,
                        onSelectionChanged = onSelectionChanged,
                        onConfirmCompleted = onConfirmCompleted,
                        onSkipCompleted = onSkipCompleted,
                        onPickJson = {
                            importJsonLauncher.launch(arrayOf("application/json"))
                        },
                        onPickOpml = { importOpmlLauncher.launch(arrayOf("*/*")) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImportDialogBody(
    state: OpmlImportState,
    onDismissRequest: () -> Unit,
    onSelectionChanged: (selectedIds: Set<String>) -> Unit,
    onConfirmCompleted: () -> Unit,
    onSkipCompleted: () -> Unit,
    onPickJson: () -> Unit,
    onPickOpml: () -> Unit
) {
    val progressHeroVisual = heroVisualFor(state)
    if (progressHeroVisual != null) {
        // Shared hero across loading → success so the checkmark continues the loader.
        ProgressFlowScaffold(
            hero = progressHeroVisual,
            state = state,
            onDone = onDismissRequest
        )
        return
    }

    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.98f,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )) togetherWith
                (fadeOut(tween(180)) +
                    scaleOut(targetScale = 0.98f, animationSpec = tween(180)))
        },
        contentKey = { contentKeyFor(it) },
        label = "import_content"
    ) { current ->
        ImportInteractiveContent(
            state = current,
            onDismissRequest = onDismissRequest,
            onSelectionChanged = onSelectionChanged,
            onConfirmCompleted = onConfirmCompleted,
            onSkipCompleted = onSkipCompleted,
            onPickJson = onPickJson,
            onPickOpml = onPickOpml
        )
    }
}

@Composable
private fun ImportInteractiveContent(
    state: OpmlImportState,
    onDismissRequest: () -> Unit,
    onSelectionChanged: (selectedIds: Set<String>) -> Unit,
    onConfirmCompleted: () -> Unit,
    onSkipCompleted: () -> Unit,
    onPickJson: () -> Unit,
    onPickOpml: () -> Unit
) {
    when (state) {
        is OpmlImportState.ShowSelector -> SelectorContent(
            onJson = onPickJson,
            onOpml = onPickOpml
        )

        is OpmlImportState.AskCompleted -> AskCompletedContent(
            state = state,
            onSelectionChanged = onSelectionChanged,
            onConfirmCompleted = onConfirmCompleted,
            onSkipCompleted = onSkipCompleted
        )

        is OpmlImportState.Error -> ErrorContent(
            message = state.message,
            onClose = onDismissRequest
        )

        else -> Unit
    }
}

private fun contentKeyFor(state: OpmlImportState): String = when (state) {
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

private fun heroVisualFor(state: OpmlImportState): ImportHeroVisual? = when (state) {
    OpmlImportState.ImportingJson,
    is OpmlImportState.Parsing -> ImportHeroVisual.Indeterminate
    is OpmlImportState.Importing -> ImportHeroVisual.Progress(state.progress.coerceIn(0f, 1f))
    is OpmlImportState.Completing -> ImportHeroVisual.Progress(state.progress.coerceIn(0f, 1f))
    is OpmlImportState.Success -> ImportHeroVisual.Complete
    else -> null
}

@Composable
private fun ProgressFlowScaffold(
    hero: ImportHeroVisual,
    state: OpmlImportState,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
            label = "import_progress_copy"
        ) { current ->
            when (current) {
                OpmlImportState.ImportingJson -> ProgressCopy(
                    title = "Restoring backup",
                    subtitle = "Bringing in shows and playback history"
                )

                is OpmlImportState.Parsing -> ProgressCopy(
                    title = "Reading OPML",
                    subtitle = "Finding podcast feeds in your file"
                )

                is OpmlImportState.Importing -> ProgressCopy(
                    title = "Importing podcasts",
                    subtitle = "Subscribing ${current.currentCount + 1} of ${current.totalCount}",
                    detail = current.currentFeedTitle
                )

                is OpmlImportState.Completing -> ProgressCopy(
                    title = "Marking history played",
                    subtitle = "Catching up past episodes on selected shows",
                    detail = current.currentShowTitle
                )

                is OpmlImportState.Success -> SuccessCopy(
                    state = current,
                    onDone = onDone
                )

                else -> Unit
            }
        }
    }
}

private sealed interface ImportHeroVisual {
    data object Indeterminate : ImportHeroVisual
    data class Progress(val value: Float) : ImportHeroVisual
    data object Complete : ImportHeroVisual
    data object Error : ImportHeroVisual
}

/**
 * Shared hero slot so the success checkmark feels like a continuation of the wavy loader:
 * progress fills to 1, then the ring resolves into a filled badge with a spring-scaled check.
 */
@Composable
private fun ImportStatusHero(
    visual: ImportHeroVisual,
    size: Dp = ImportHeroSize
) {
    val isComplete = visual is ImportHeroVisual.Complete
    val isError = visual is ImportHeroVisual.Error

    var showCheck by remember { mutableStateOf(false) }
    val ringProgress = remember { Animatable(0f) }

    LaunchedEffect(visual) {
        when (visual) {
            is ImportHeroVisual.Progress -> {
                showCheck = false
                ringProgress.snapTo(visual.value.coerceIn(0f, 1f))
            }
            ImportHeroVisual.Indeterminate -> {
                showCheck = false
            }
            ImportHeroVisual.Complete -> {
                // Finish the ring, then reveal the check in the same geometry.
                ringProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                )
                delay(60)
                showCheck = true
            }
            ImportHeroVisual.Error -> {
                showCheck = false
            }
        }
    }

    // Keep determinate ring tracking live progress while importing.
    LaunchedEffect((visual as? ImportHeroVisual.Progress)?.value) {
        val progressVisual = visual as? ImportHeroVisual.Progress ?: return@LaunchedEffect
        ringProgress.animateTo(
            targetValue = progressVisual.value.coerceIn(0f, 1f),
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        )
    }

    val checkScale by animateFloatAsState(
        targetValue = if (showCheck) 1f else 0.55f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "check_scale"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (showCheck) 1f else 0f,
        animationSpec = tween(280),
        label = "check_alpha"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when {
            isError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            isComplete -> {
                // Keep the wavy ring at full progress under the badge so the motion continues.
                BoxLoreLoader.CircularWavy(
                    progress = ringProgress.value,
                    size = size,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                            alpha = checkAlpha
                        }
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            visual is ImportHeroVisual.Progress -> {
                BoxLoreLoader.CircularWavy(
                    progress = ringProgress.value,
                    size = size,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            else -> {
                BoxLoreLoader.CircularWavy(
                    size = size,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ProgressCopy(
    title: String,
    subtitle: String,
    detail: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        if (!detail.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(28.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ImportCorner,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp
            ) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectorContent(
    onJson: () -> Unit,
    onOpml: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.LibraryBooks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Import library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Restore a boxlore backup, or migrate shows from another podcast app.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        ImportOptionCard(
            icon = Icons.Rounded.SettingsBackupRestore,
            title = "boxlore backup",
            subtitle = "JSON restore with subscriptions, likes, and playback history",
            badge = ".json",
            onClick = onJson
        )
        Spacer(modifier = Modifier.height(12.dp))
        ImportOptionCard(
            icon = Icons.Rounded.ImportExport,
            title = "Other app export",
            subtitle = "OPML from Apple Podcasts, Spotify, Pocket Casts, and more",
            badge = ".opml",
            onClick = onOpml
        )
    }
}

@Composable
private fun ImportOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ImportCorner,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AskCompletedContent(
    state: OpmlImportState.AskCompleted,
    onSelectionChanged: (Set<String>) -> Unit,
    onConfirmCompleted: () -> Unit,
    onSkipCompleted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp)
    ) {
        Text(
            text = "Start fresh?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Mark past episodes as played on selected shows so your queue stays focused on new releases.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = {
                    onSelectionChanged(state.importedPodcasts.map { it.id }.toSet())
                },
                label = { Text("Select all") },
                icon = {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(50)
            )
            SuggestionChip(
                onClick = { onSelectionChanged(emptySet()) },
                label = { Text("Deselect all") },
                icon = {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                },
                shape = RoundedCornerShape(50)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = ImportCorner,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(state.importedPodcasts, key = { it.id }) { podcast ->
                    val isChecked = podcast.id in state.selectedIds
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (isChecked) {
                                    state.selectedIds - podcast.id
                                } else {
                                    state.selectedIds + podcast.id
                                }
                                onSelectionChanged(next)
                            },
                        headlineContent = {
                            Text(
                                text = podcast.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                text = podcast.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                OptimizedImage(
                                    url = podcast.imageUrl,
                                    proxyWidth = 150,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        },
                        trailingContent = {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val next = if (checked) {
                                        state.selectedIds + podcast.id
                                    } else {
                                        state.selectedIds - podcast.id
                                    }
                                    onSelectionChanged(next)
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onConfirmCompleted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Mark selected (${state.selectedIds.size}) as played",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        TextButton(
            onClick = onSkipCompleted,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "Keep all unplayed",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun hasPostNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun SuccessCopy(
    state: OpmlImportState.Success,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val showNotificationPrompt = state.isJson &&
        state.hasNotificationsEnabled &&
        !hasPostNotificationPermission(context)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "You're all set",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = successSubtitle(state.isJson),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun successSubtitle(isJson: Boolean): String {
    return if (isJson) {
        "Your backup is restored and ready to listen."
    } else {
        "Your shows are imported and ready to listen."
    }
}

@Composable
private fun ImportSuccessSummary(state: OpmlImportState.Success) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ImportCorner,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SummaryRow(label = "Imported shows", value = "${state.importedCount}")
            if (!state.isJson && state.completedCount > 0) {
                SummaryRow(label = "Marked played", value = "${state.completedCount}")
            }
        }
    }
}

@Composable
private fun ImportNotificationPermissionCard() {
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            AnalyticsHelper.trackNotificationPermissionDecided(granted)
        }
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ImportCorner,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enable notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "This backup includes shows with alerts or auto-downloads. Allow notifications so they can keep working in the background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Grant permission", color = MaterialTheme.colorScheme.onError)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ImportStatusHero(visual = ImportHeroVisual.Error)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Import failed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ImportCorner,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Close",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
