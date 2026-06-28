package cx.aswin.boxcast.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import cx.aswin.boxcast.core.designsystem.components.OptimizedImage
import cx.aswin.boxcast.core.model.Episode
import kotlinx.coroutines.launch

@Composable
fun MiniPlayerContent(
    episode: Episode,
    podcastTitle: String,
    podcastImageUrl: String?,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: Long,
    duration: Long,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Main content row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art - circular with fallback to podcast image
            val imageUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcastImageUrl
            OptimizedImage(
                url = imageUrl,
                proxyWidth = 88, // 44.dp * 2 for retina sharpness
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceVariant)
                    .graphicsLayer { alpha = if (isLoading) 0.6f else 1f }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            MiniPlayerTrackDetails(
                episodeTitle = episode.title,
                podcastTitle = podcastTitle,
                colorScheme = colorScheme,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            MiniPlayerActionButtons(
                isPlaying = isPlaying,
                isLoading = isLoading,
                colorScheme = colorScheme,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext
            )
        }
        
        // Progress bar at bottom - standard LinearProgressIndicator
        if (duration > 0) {
            val progress = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = colorScheme.primary,
                trackColor = colorScheme.primary.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun MiniPlayerTrackDetails(
    episodeTitle: String,
    podcastTitle: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = episodeTitle.replace("+", " "),
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            ),
            color = colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = podcastTitle.replace("+", " "),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                letterSpacing = 0.sp
            ),
            color = colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MiniPlayerActionButtons(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val seekBackScale = remember { Animatable(1f) }
    val seekForwardScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Seek Back -10s button
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { 
                    scaleX = seekBackScale.value
                    scaleY = seekBackScale.value
                }
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.2f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    enabled = !isLoading
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        launch {
                            seekBackScale.animateTo(0.8f, tween(80))
                            seekBackScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        }
                        launch {
                            seekForwardScale.animateTo(0.95f, tween(60))
                            seekForwardScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                        }
                    }
                    onPrevious()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Replay10,
                contentDescription = "Seek back 10 seconds",
                tint = colorScheme.primary.copy(alpha = if (isLoading) 0.5f else 1f),
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Play/Pause button
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseAnimatedScale by infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        val pulseScale = if (isLoading) pulseAnimatedScale else 1f
        
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                .clip(CircleShape)
                .background(colorScheme.primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    enabled = !isLoading
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = colorScheme.onPrimary.copy(alpha = if (isLoading) 0.6f else 1f),
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Seek Forward +30s button
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { 
                    scaleX = seekForwardScale.value
                    scaleY = seekForwardScale.value
                }
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.2f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    enabled = !isLoading
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        launch {
                            seekForwardScale.animateTo(0.8f, tween(80))
                            seekForwardScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        }
                        launch {
                            seekBackScale.animateTo(0.95f, tween(60))
                            seekBackScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                        }
                    }
                    onNext()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Forward30,
                contentDescription = "Seek forward 30 seconds",
                tint = colorScheme.primary.copy(alpha = if (isLoading) 0.5f else 1f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

