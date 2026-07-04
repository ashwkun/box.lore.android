package cx.aswin.boxcast.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.data.TranscriptSegment

@Composable
fun TranscriptView(
    transcript: List<TranscriptSegment>,
    positionFlow: kotlinx.coroutines.flow.Flow<Long>,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    isSyncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    transcriptUrl: String? = null
) {
    val positionMs by positionFlow.collectAsState(initial = 0L)
    val listState = rememberLazyListState()
    var clickedIndex by remember { mutableStateOf<Int?>(null) }
    
    val activeIndex by remember(transcript, clickedIndex) {
        derivedStateOf {
            clickedIndex ?: transcript.indexOfFirst { positionMs >= it.startMs && positionMs <= it.endMs }
        }
    }

    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            onSyncEnabledChange(false)
        }
    }
    
    LaunchedEffect(positionMs) {
        clickedIndex?.let { idx ->
            val segment = transcript.getOrNull(idx)
            if (segment != null && positionMs >= segment.startMs && positionMs <= segment.endMs) {
                clickedIndex = null
            }
        }
    }
    
    LaunchedEffect(clickedIndex) {
        if (clickedIndex != null) {
            delay(1000)
            clickedIndex = null
        }
    }
    
    TranscriptScrollEffect(listState, activeIndex, isSyncEnabled)
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (transcript.isEmpty()) {
            if (transcriptUrl != null) {
                CircularProgressIndicator(
                    color = colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Text(
                    text = "No transcript available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithContent {
                        drawContent()
                        val fadeHeight = 48.dp.toPx()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadeHeight
                            ),
                            blendMode = BlendMode.DstIn
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - fadeHeight,
                                endY = size.height
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(transcript) { index, segment ->
                    TranscriptSegmentItem(
                        segment = segment,
                        isActive = index == activeIndex,
                        colorScheme = colorScheme,
                        onClick = {
                            onSeek(segment.startMs)
                            clickedIndex = index
                            onSyncEnabledChange(true)
                        }
                    )
                }
            }

            TranscriptSyncButton(
                isSyncEnabled = isSyncEnabled,
                colorScheme = colorScheme,
                onClick = { onSyncEnabledChange(true) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun TranscriptScrollEffect(
    listState: LazyListState,
    activeIndex: Int,
    isSyncEnabled: Boolean
) {
    LaunchedEffect(activeIndex, isSyncEnabled) {
        if (activeIndex != -1 && isSyncEnabled) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
                val itemHeight = itemInfo?.size ?: 100
                val focusOffset = - (viewportHeight * 0.28f).toInt() + (itemHeight / 2)
                listState.animateScrollToItem(activeIndex, focusOffset)
            } else {
                val scrollIndex = (activeIndex - 2).coerceAtLeast(0)
                listState.animateScrollToItem(scrollIndex)
            }
        }
    }
}

@Composable
private fun TranscriptSegmentItem(
    segment: TranscriptSegment,
    isActive: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (isActive) colorScheme.primary else colorScheme.onSurface,
        animationSpec = tween(durationMillis = 300),
        label = "textColor"
    )
    
    val textScale by animateFloatAsState(
        targetValue = if (isActive) 1.03f else 1.0f,
        animationSpec = tween(durationMillis = 300),
        label = "textScale"
    )
    val textOpacity by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.35f,
        animationSpec = tween(durationMillis = 300),
        label = "textOpacity"
    )
    
    val textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .graphicsLayer {
                scaleX = textScale
                scaleY = textScale
                alpha = textOpacity
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
    ) {
        Text(
            text = segment.text,
            style = textStyle,
            color = textColor
        )
    }
}

@Composable
private fun TranscriptSyncButton(
    isSyncEnabled: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = !isSyncEnabled,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(colorScheme.secondaryContainer)
                .border(
                    width = 1.dp,
                    color = colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Sync Scroll",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun FullscreenTranscriptScreen(
    transcript: List<TranscriptSegment>,
    positionFlow: kotlinx.coroutines.flow.Flow<Long>,
    isPlaying: Boolean,
    isLoading: Boolean,
    durationMs: Long,
    colorScheme: ColorScheme,
    isSyncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    transcriptUrl: String? = null
) {
    val positionMs by positionFlow.collectAsState(initial = 0L)
    val containerColor = colorScheme.surface
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(containerColor)
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Transcript",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // Transcript View (Expanded)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            TranscriptView(
                transcript = transcript,
                positionFlow = positionFlow,
                colorScheme = colorScheme,
                onSeek = onSeek,
                isSyncEnabled = isSyncEnabled,
                onSyncEnabledChange = onSyncEnabledChange,
                modifier = Modifier.fillMaxSize(),
                transcriptUrl = transcriptUrl
            )
        }
        
        // Bottom controls (Pause/Play and Seek only)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorScheme.surfaceContainerHigh)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Seek bar
            if (durationMs > 0) {
                Slider(
                    value = positionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.primary,
                        activeTrackColor = colorScheme.primary,
                        inactiveTrackColor = colorScheme.primary.copy(alpha = 0.2f)
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(positionMs),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.primary.copy(alpha = 0.85f)
                    )
                    Text(
                        text = "-" + formatTime((durationMs - positionMs).coerceAtLeast(0)),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colorScheme.primary.copy(alpha = 0.85f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            PlayerControls(
                isPlaying = isPlaying,
                isLoading = isLoading,
                colorScheme = colorScheme,
                controlTint = colorScheme.primary,
                onPlayPause = onPlayPause,
                onPrevious = { onSeek((positionMs - 10000L).coerceAtLeast(0L)) },
                onNext = { onSeek((positionMs + 30000L).coerceAtMost(durationMs)) },
                height = 72.dp
            )
        }
    }
}
