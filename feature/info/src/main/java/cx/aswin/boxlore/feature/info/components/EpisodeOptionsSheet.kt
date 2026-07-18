package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToQueue
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeOptionsSheet(
    episode: Episode,
    isLiked: Boolean,
    onDismissRequest: () -> Unit,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onQueue: () -> Unit,
    onShare: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = { WindowInsets.navigationBars },
    ) {
        Column(
            modifier =
                Modifier
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Header: Episode Info
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = MaterialTheme.shapes.medium, // Or ExpressiveShapes.Squircle
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    OptimizedImage(
                        url = episode.imageUrl,
                        proxyWidth = 150, // 64dp thumbnail
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Episode Options",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Actions
            OptionItem(
                icon = Icons.Outlined.PlayCircle,
                label = "Play Episode",
                onClick = {
                    onPlay()
                    onDismissRequest()
                },
            )

            OptionItem(
                icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                label = if (isLiked) "Liked" else "Like",
                iconTint = if (isLiked) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onToggleLike()
                    // Don't auto-dismiss for like toggle, maybe? Or do.
                    // Usually options sheet dismisses. Let's keep it open for toggling?
                    // No, standard UX is click -> execute. Toggle like is usually immediate.
                    // But if I want to visualize it, I should update state.
                    // The state comes from parent, so it updates automatically.
                },
            )

            OptionItem(
                icon = Icons.Outlined.AddToQueue,
                label = "Add to Queue",
                onClick = {
                    onQueue()
                    onDismissRequest()
                },
            )

            OptionItem(
                icon = Icons.Outlined.Download,
                label = "Download",
                onClick = {
                    onDownload()
                    onDismissRequest()
                },
            )

            OptionItem(
                icon = Icons.Outlined.Share,
                label = "Share",
                onClick = {
                    onShare()
                    onDismissRequest()
                },
            )
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .expressiveClickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
