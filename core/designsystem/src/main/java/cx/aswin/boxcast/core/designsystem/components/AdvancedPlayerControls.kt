package cx.aswin.boxcast.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape



enum class ControlStyle {
    Squircle, // Primary Player Style (Primary Container)
    Outlined, // (Legacy/Alternative)
    TonalSquircle, // (Heavy weight)
    Transparent // (Lighter weight - Simply Icons)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdvancedPlayerControls(
    isLiked: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    colorScheme: ColorScheme,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onQueueClick: () -> Unit,
    style: ControlStyle = ControlStyle.Squircle,
    overrideColor: Color? = null, // Optional partial override for Icon Tints
    horizontalArrangement: Arrangement.Horizontal? = null,
    showAddQueueIcon: Boolean = false,
    isQueued: Boolean = false,
    showShareButton: Boolean = true,
    isPlayed: Boolean = false,
    showMarkPlayedButton: Boolean = true,
    onMarkPlayedClick: (() -> Unit)? = null,
    controlSize: androidx.compose.ui.unit.Dp? = null,
    modifier: Modifier = Modifier
) {
    val defaultArrangement = if (style == ControlStyle.Outlined) {
        Arrangement.SpaceEvenly 
    } else {
        Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.CenterHorizontally)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement ?: defaultArrangement,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Defines base tint based on style or override
        val baseActiveTint = overrideColor ?: if (style == ControlStyle.Outlined) Color(0xFFE91E63) else colorScheme.primary
        val baseInactiveTint = overrideColor ?: if (style == ControlStyle.Outlined || style == ControlStyle.TonalSquircle || style == ControlStyle.Transparent) colorScheme.onSurfaceVariant else colorScheme.primary
        
        // --- Reordered for thumb reachability: low-priority LEFT → high-priority RIGHT ---
        
        // 1. MARK PLAYED (leftmost — least frequent action)
        if (onMarkPlayedClick != null && showMarkPlayedButton) {
             AdaptiveControlButton(
                 style = style,
                 isActive = isPlayed,
                 isLoading = false,
                 colorScheme = colorScheme,
                 activeIcon = androidx.compose.material.icons.Icons.Rounded.CheckCircle,
                 inactiveIcon = androidx.compose.material.icons.Icons.Outlined.CheckCircle,
                 contentDescription = if (isPlayed) "Mark Unplayed" else "Mark Played",
                 activeTint = if (style == ControlStyle.TonalSquircle && overrideColor == null) colorScheme.onTertiaryContainer else baseActiveTint,
                 inactiveTint = baseInactiveTint,
                 activeContainerColor = if (style == ControlStyle.TonalSquircle) {
                    if (overrideColor != null) colorScheme.primaryContainer else colorScheme.tertiaryContainer
                 } else Color.Unspecified,
                 controlSize = controlSize,
                 onClick = onMarkPlayedClick
             )
        } else if (style != ControlStyle.Squircle && showShareButton) {
             AdaptiveControlButton(
                 style = style,
                 isActive = false,
                 isLoading = false,
                 colorScheme = colorScheme,
                 activeIcon = androidx.compose.material.icons.Icons.Outlined.Share,
                 inactiveIcon = androidx.compose.material.icons.Icons.Outlined.Share,
                 contentDescription = "Share",
                 activeTint = baseInactiveTint,
                 inactiveTint = baseInactiveTint,
                 controlSize = controlSize,
                 onClick = { /* TODO layer */ }
             )
         }

        // 2. LIKE
        AdaptiveControlButton(
            style = style,
            isActive = isLiked,
            isLoading = false,
            colorScheme = colorScheme,
            activeIcon = Icons.Default.Favorite,
            inactiveIcon = Icons.Outlined.FavoriteBorder,
            contentDescription = "Like",
            activeTint = if (style == ControlStyle.TonalSquircle && overrideColor == null) colorScheme.onTertiaryContainer else baseActiveTint,
            inactiveTint = baseInactiveTint,
            activeContainerColor = if (style == ControlStyle.TonalSquircle) {
                if (overrideColor != null) colorScheme.primaryContainer else colorScheme.tertiaryContainer
            } else Color.Unspecified,
            controlSize = controlSize,
            onClick = onLikeClick
        )
        
        // 3. DOWNLOAD
        AdaptiveControlButton(
            style = style,
            isActive = isDownloaded,
            isLoading = isDownloading,
            colorScheme = colorScheme,
            activeIcon = Icons.Rounded.DownloadDone,
            inactiveIcon = Icons.Rounded.Download,
            contentDescription = "Download",
            activeTint = if (style == ControlStyle.TonalSquircle && overrideColor == null) colorScheme.onTertiaryContainer else baseActiveTint,
            inactiveTint = baseInactiveTint,
            activeContainerColor = if (style == ControlStyle.TonalSquircle) {
                if (overrideColor != null) colorScheme.primaryContainer else colorScheme.tertiaryContainer
            } else Color.Unspecified,
            controlSize = controlSize,
            onClick = onDownloadClick
        )

        // 4. QUEUE/ADD TO QUEUE (rightmost — closest to play button, most frequent)
        val queueIcon = if (isQueued) {
            androidx.compose.material.icons.Icons.Rounded.PlaylistAddCheck
        } else if (showAddQueueIcon || style == ControlStyle.TonalSquircle || style == ControlStyle.Transparent) {
            androidx.compose.material.icons.Icons.AutoMirrored.Rounded.PlaylistAdd
        } else {
            androidx.compose.material.icons.Icons.AutoMirrored.Rounded.QueueMusic
        }
        
        AdaptiveControlButton(
            style = style,
            isActive = isQueued, 
            isLoading = false,
            colorScheme = colorScheme,
            activeIcon = queueIcon,
            inactiveIcon = queueIcon,
            contentDescription = if (isQueued) "Added to Queue" else if (showAddQueueIcon || style == ControlStyle.TonalSquircle || style == ControlStyle.Transparent) "Add to Queue" else "Queue",
            activeTint = if (style == ControlStyle.TonalSquircle && overrideColor == null) colorScheme.onTertiaryContainer else baseActiveTint, 
            inactiveTint = baseInactiveTint,
            activeContainerColor = if (style == ControlStyle.TonalSquircle) {
                if (overrideColor != null) colorScheme.primaryContainer else colorScheme.tertiaryContainer
            } else Color.Unspecified,
            controlSize = controlSize,
            onClick = {
                android.util.Log.d("AdvancedPlayerControls", "Queue button clicked: isQueued=$isQueued, showAddQueueIcon=$showAddQueueIcon, style=$style")
                onQueueClick()
            }
        )
    }
}


@Composable
fun AdaptiveControlButton(
    style: ControlStyle,
    isActive: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    activeTint: Color,
    inactiveTint: Color,
    activeContainerColor: Color = Color.Unspecified,
    controlSize: androidx.compose.ui.unit.Dp? = null,
    onClick: () -> Unit
) {
    if (style == ControlStyle.Transparent) {
        // TRANSPARENT Style (Just Icons, Expressive Click)
        // No container, just the icon with standard touch target
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .size(controlSize ?: 48.dp) // Standard Minimum Touch Target or Custom
                .expressiveClickable(onClick = onClick)
        ) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith 
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                    },
                    label = "loader_transition"
                ) { loading ->
                    if (loading) {
                        cx.aswin.boxcast.core.designsystem.components.BoxCastLoader.CircularWavy(
                            modifier = Modifier.size(24.dp),
                            color = activeTint,
                            trackColor = activeTint.copy(alpha = 0.2f)
                        )
                    } else {
                        Icon(
                            imageVector = if (isActive) activeIcon else inactiveIcon,
                            contentDescription = contentDescription,
                            tint = if (isActive) activeTint else inactiveTint,
                            modifier = Modifier.size((controlSize ?: 48.dp) * 0.58f)
                        )
                    }
                }
            }
        }
        return
    }

    if (style == ControlStyle.Squircle || style == ControlStyle.TonalSquircle) {
        // Squircle Shape
        // Player (Squircle): 14dp radius, Primary Container (alpha 0.15)
        // TonalSquircle: 18dp radius, SurfaceContainerHigh default, TertiaryContainer active
        
        val cornerRadius = if (style == ControlStyle.TonalSquircle) 18.dp else 14.dp
        
        val squircleShape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = cornerRadius, smoothnessAsPercentTL = 60,
            cornerRadiusTR = cornerRadius, smoothnessAsPercentTR = 60,
            cornerRadiusBL = cornerRadius, smoothnessAsPercentBL = 60,
            cornerRadiusBR = cornerRadius, smoothnessAsPercentBR = 60
        )
        
        val containerColor = if (isActive && activeContainerColor != Color.Unspecified) {
            activeContainerColor 
        } else {
             if (style == ControlStyle.TonalSquircle) colorScheme.surfaceContainerHigh else colorScheme.primary.copy(alpha = 0.15f)
        }
        
        val size = controlSize ?: if (style == ControlStyle.TonalSquircle) 56.dp else 48.dp
        val iconSize = if (controlSize != null) size * 0.5f else if (style == ControlStyle.TonalSquircle) 26.dp else 24.dp
        
        Surface(
            color = containerColor,
            shape = squircleShape,
            modifier = Modifier
                .size(size)
                .expressiveClickable(onClick = onClick)
        ) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isLoading,
                    transitionSpec = {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith 
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                    },
                    label = "loader_transition"
                ) { loading ->
                    if (loading) {
                        cx.aswin.boxcast.core.designsystem.components.BoxCastLoader.CircularWavy(
                            modifier = Modifier.size(iconSize - 2.dp),
                            color = activeTint,
                            trackColor = activeTint.copy(alpha = 0.2f)
                        )
                    } else {
                        Icon(
                            imageVector = if (isActive) activeIcon else inactiveIcon,
                            contentDescription = contentDescription,
                            tint = if (isActive) activeTint else inactiveTint,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
        }
    } else {
        // OUTLINED Style
        OutlinedIconButton(
            onClick = onClick,
            border = BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            androidx.compose.animation.AnimatedContent(
                targetState = isLoading,
                transitionSpec = {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith 
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                },
                label = "loader_transition"
            ) { loading ->
                if (loading) {
                     cx.aswin.boxcast.core.designsystem.components.BoxCastLoader.CircularWavy(
                        modifier = Modifier.size(20.dp),
                        color = colorScheme.primary,
                        trackColor = colorScheme.primary.copy(alpha = 0.2f)
                    )
                } else {
                    Icon(
                        imageVector = if (isActive) activeIcon else inactiveIcon,
                        contentDescription = contentDescription,
                        tint = if (isActive) activeTint else inactiveTint
                    )
                }
            }
        }
    }
}
