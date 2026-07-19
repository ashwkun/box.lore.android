package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import kotlinx.coroutines.delay

private val ImportHeroSize = 80.dp

internal sealed interface ImportHeroVisual {
    data object Indeterminate : ImportHeroVisual

    data class Progress(
        val value: Float,
    ) : ImportHeroVisual

    data object Complete : ImportHeroVisual

    data object Error : ImportHeroVisual
}

/**
 * Shared hero slot so the success checkmark feels like a continuation of the wavy loader:
 * progress fills to 1, then the ring resolves into a filled badge with a spring-scaled check.
 */
@Composable
internal fun ImportStatusHero(
    visual: ImportHeroVisual,
    size: Dp = ImportHeroSize,
) {
    val animation = rememberImportHeroAnimation(visual)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        ImportStatusHeroBody(
            visual = visual,
            size = size,
            animation = animation,
        )
    }
}

@Composable
private fun rememberImportHeroAnimation(visual: ImportHeroVisual): ImportHeroAnimation {
    var showCheck by remember { mutableStateOf(false) }
    val ringProgress = remember { Animatable(0f) }

    LaunchedEffect(visual) {
        when (visual) {
            is ImportHeroVisual.Progress -> {
                showCheck = false
                ringProgress.animateTo(
                    targetValue = visual.value.coerceIn(0f, 1f),
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                )
            }
            ImportHeroVisual.Indeterminate -> {
                showCheck = false
            }
            ImportHeroVisual.Complete -> {
                // Finish the ring, then reveal the check in the same geometry.
                ringProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                )
                delay(60)
                showCheck = true
            }
            ImportHeroVisual.Error -> {
                showCheck = false
            }
        }
    }

    val checkScale by animateFloatAsState(
        targetValue = if (showCheck) 1f else 0.55f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "check_scale",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (showCheck) 1f else 0f,
        animationSpec = tween(280),
        label = "check_alpha",
    )

    return ImportHeroAnimation(
        ringProgress = ringProgress.value,
        checkScale = checkScale,
        checkAlpha = checkAlpha,
    )
}

private data class ImportHeroAnimation(
    val ringProgress: Float,
    val checkScale: Float,
    val checkAlpha: Float,
)

@Composable
private fun ImportStatusHeroBody(
    visual: ImportHeroVisual,
    size: Dp,
    animation: ImportHeroAnimation,
) {
    when (visual) {
        ImportHeroVisual.Error -> ImportErrorHero()
        ImportHeroVisual.Complete -> ImportCompleteHero(size, animation)
        is ImportHeroVisual.Progress -> ImportProgressHero(size, animation.ringProgress)
        ImportHeroVisual.Indeterminate -> ImportIndeterminateHero(size)
    }
}

@Composable
private fun ImportErrorHero() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ImportCompleteHero(
    size: Dp,
    animation: ImportHeroAnimation,
) {
    // Keep the wavy ring at full progress under the badge so the motion continues.
    ImportProgressHero(size, animation.ringProgress)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animation.checkScale
                    scaleY = animation.checkScale
                    alpha = animation.checkAlpha
                }.background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ImportProgressHero(
    size: Dp,
    progress: Float,
) {
    BoxLoreLoader.CircularWavy(
        progress = progress,
        size = size,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ImportIndeterminateHero(size: Dp) {
    BoxLoreLoader.CircularWavy(
        size = size,
        color = MaterialTheme.colorScheme.primary,
    )
}
