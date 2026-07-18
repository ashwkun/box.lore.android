package cx.aswin.boxlore.feature.home.components

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private const val TAG = "StylizedLogo"

/**
 * Collapsing M3-aligned Top Bar with stylized variable logo and profile.
 *
 * @param scrollFraction 0f = fully expanded (roomier), 1f = fully collapsed
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TopControlBar(
    scrollFractionProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
    onFeedbackClick: () -> Unit = {},
    onFeedbackLongClick: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onAvatarLongClick: () -> Unit = {},
) {
    // Expanded state: roomier padding, surface color
    // Collapsed state: compact padding, surfaceContainerLow color
    val expandedPadding = 16.dp
    val collapsedPadding = 8.dp

    val expandedColor = MaterialTheme.colorScheme.surface
    val collapsedColor = MaterialTheme.colorScheme.surfaceContainerLow

    // Update system status bar icon color to match background (evaluated once per theme change, not on scroll)
    val view = LocalView.current
    if (!view.isInEditMode) {
        val isLightStatusBar =
            remember(expandedColor, collapsedColor) {
                val luminance = (
                    0.299f * expandedColor.red +
                        0.587f * expandedColor.green +
                        0.114f * expandedColor.blue
                )
                luminance > 0.5f
            }
        LaunchedEffect(isLightStatusBar) {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    remember(expandedColor, collapsedColor) {
                        Modifier.drawBehind {
                            val fraction = scrollFractionProvider().coerceIn(0f, 1f)
                            val color = lerp(expandedColor, collapsedColor, fraction)
                            drawRect(color)
                        }
                    },
                ).statusBarsPadding()
                .layout { measurable, constraints ->
                    val fraction = scrollFractionProvider().coerceIn(0f, 1f)
                    val currentPadding =
                        androidx.compose.ui.unit
                            .lerp(expandedPadding, collapsedPadding, fraction)
                    val paddingPx = currentPadding.roundToPx()

                    val placeable =
                        measurable.measure(
                            constraints.copy(
                                minWidth = constraints.minWidth,
                                maxWidth = constraints.maxWidth,
                            ),
                        )

                    val height = placeable.height + paddingPx * 2
                    layout(placeable.width, height) {
                        placeable.place(0, paddingPx)
                    }
                }.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Left Section (Logo)
        cx.aswin.boxlore.core.designsystem.components
            .BoxLoreLogo()

        // Right Side Controls (Toggle + Feedback + Settings)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val feedbackInteractionSource = remember { MutableInteractionSource() }
                val isFeedbackPressed by feedbackInteractionSource.collectIsPressedAsState()
                val feedbackScale by animateFloatAsState(
                    targetValue = if (isFeedbackPressed) 0.90f else 1f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    label = "feedbackBounce",
                )

                // Feedback
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                scaleX = feedbackScale
                                scaleY = feedbackScale
                            }.clip(CircleShape)
                            .combinedClickable(
                                interactionSource = feedbackInteractionSource,
                                indication = androidx.compose.foundation.LocalIndication.current,
                                onClick = onFeedbackClick,
                                onLongClick = onFeedbackLongClick,
                            ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = "Send Feedback",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val settingsInteractionSource = remember { MutableInteractionSource() }
                val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()
                val settingsScale by animateFloatAsState(
                    targetValue = if (isSettingsPressed) 0.90f else 1f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    label = "settingsBounce",
                )

                // Profile/Settings
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .testTag("home_settings_button")
                            .graphicsLayer {
                                scaleX = settingsScale
                                scaleY = settingsScale
                            }.clip(CircleShape)
                            .combinedClickable(
                                interactionSource = settingsInteractionSource,
                                indication = androidx.compose.foundation.LocalIndication.current,
                                onClick = onAvatarClick,
                                onLongClick = onAvatarLongClick,
                            ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
