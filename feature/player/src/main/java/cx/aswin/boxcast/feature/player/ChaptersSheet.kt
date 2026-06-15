package cx.aswin.boxcast.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import cx.aswin.boxcast.core.model.Chapter

@Composable
fun ChaptersSheetContent(
    chapters: List<Chapter>,
    positionMs: Long,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    chaptersUrl: String? = null,
    isChaptersLoading: Boolean = false,
    hasTranscript: Boolean = false,
    onGenerateChapters: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chapters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                TextButton(onClick = onClose) {
                    Text("Close", color = colorScheme.primary)
                }
            }

            if (isChaptersLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else if (chapters.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No chapters available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (hasTranscript) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onClose()
                                onGenerateChapters()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primaryContainer,
                                contentColor = colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Generate AI Chapters",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        val startMs = (chapter.startTime * 1000).toLong()
                        val endMs = if (index < chapters.size - 1) {
                            (chapters[index + 1].startTime * 1000).toLong()
                        } else {
                            Long.MAX_VALUE
                        }
                        val isActive = positionMs >= startMs && positionMs < endMs
                        
                        ChapterRow(
                            chapter = chapter,
                            startMs = startMs,
                            isActive = isActive,
                            colorScheme = colorScheme,
                            onClick = { onSeek(startMs) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterRow(
    chapter: Chapter,
    startMs: Long,
    isActive: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) {
        colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.2f)
    }
    
    val contentColor = if (isActive) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.onSurface
    }
    
    val shape = RoundedCornerShape(12.dp)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Image
        if (!chapter.img.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(chapter.img)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(startMs),
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
