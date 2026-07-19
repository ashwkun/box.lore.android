package cx.aswin.boxlore.ui.libraryimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.model.Podcast

sealed interface OpmlImportState {
    data object Idle : OpmlImportState

    data object ShowSelector : OpmlImportState

    data object ImportingJson : OpmlImportState

    data class Parsing(
        val uri: android.net.Uri,
    ) : OpmlImportState

    data class Importing(
        val currentFeedTitle: String,
        val progress: Float,
        val currentCount: Int,
        val totalCount: Int,
        val importedPodcasts: List<Podcast>,
    ) : OpmlImportState

    data class AskCompleted(
        val importedPodcasts: List<Podcast>,
        val selectedIds: Set<String>,
    ) : OpmlImportState

    data class Completing(
        val progress: Float,
        val currentShowTitle: String,
        val podcastsToMark: List<Podcast>,
        val totalImportedCount: Int,
        val importedPodcasts: List<Podcast> = emptyList(),
    ) : OpmlImportState

    data class Success(
        val importedCount: Int,
        val completedCount: Int,
        val isJson: Boolean = false,
        val importedPodcasts: List<Podcast> = emptyList(),
        val hasNotificationsEnabled: Boolean = false,
    ) : OpmlImportState

    data class Error(
        val message: String,
    ) : OpmlImportState
}

private fun canDismissImportState(state: OpmlImportState): Boolean =
    when (state) {
        is OpmlImportState.ShowSelector,
        is OpmlImportState.AskCompleted,
        is OpmlImportState.Success,
        is OpmlImportState.Error,
        -> true
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
    onImportOpmlSelected: (android.net.Uri) -> Unit,
) {
    if (state is OpmlImportState.Idle) return

    val importJsonLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri -> uri?.let { onImportJsonSelected(it) } },
        )
    val importOpmlLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri -> uri?.let { onImportOpmlSelected(it) } },
        )
    val canDismiss = canDismissImportState(state)

    Dialog(
        onDismissRequest = {
            if (canDismiss) onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
            ) { innerPadding ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    if (canDismiss) {
                        ImportCloseButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.align(Alignment.TopEnd),
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
                        onPickOpml = { importOpmlLauncher.launch(arrayOf("*/*")) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                ),
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onPickOpml: () -> Unit,
) {
    val progressHeroVisual = heroVisualFor(state)
    if (progressHeroVisual != null) {
        // Shared hero across loading → success so the checkmark continues the loader.
        ProgressFlowScaffold(
            hero = progressHeroVisual,
            state = state,
            onDone = onDismissRequest,
        )
        return
    }

    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (
                fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        initialScale = 0.98f,
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    )
            ) togetherWith
                (
                    fadeOut(tween(180)) +
                        scaleOut(targetScale = 0.98f, animationSpec = tween(180))
                )
        },
        contentKey = { contentKeyFor(it) },
        label = "import_content",
    ) { current ->
        ImportInteractiveContent(
            state = current,
            onDismissRequest = onDismissRequest,
            onSelectionChanged = onSelectionChanged,
            onConfirmCompleted = onConfirmCompleted,
            onSkipCompleted = onSkipCompleted,
            onPickJson = onPickJson,
            onPickOpml = onPickOpml,
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
    onPickOpml: () -> Unit,
) {
    when (state) {
        is OpmlImportState.ShowSelector ->
            SelectorContent(
                onJson = onPickJson,
                onOpml = onPickOpml,
            )

        is OpmlImportState.AskCompleted ->
            AskCompletedContent(
                state = state,
                onSelectionChanged = onSelectionChanged,
                onConfirmCompleted = onConfirmCompleted,
                onSkipCompleted = onSkipCompleted,
            )

        is OpmlImportState.Error ->
            ErrorContent(
                message = state.message,
                onClose = onDismissRequest,
            )

        else -> Unit
    }
}
