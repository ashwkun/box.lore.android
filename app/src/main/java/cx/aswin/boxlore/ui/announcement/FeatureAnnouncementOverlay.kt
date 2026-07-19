package cx.aswin.boxlore.ui.announcement

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.component.ExpressiveAnimatedBackground
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLogo
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import kotlinx.coroutines.launch

/** Full-screen PostHog-gated feature announcement overlay (e.g. Android Auto). */
@Composable
fun FeatureAnnouncementOverlay(
    featureAnnouncementId: String,
    userPrefs: UserPreferencesRepository,
) {
    val scope = rememberCoroutineScope()
    val overlayAlpha = remember { Animatable(0f) }
    val phase1 = remember { Animatable(0f) }
    val phase2 = remember { Animatable(0f) }
    val phase3 = remember { Animatable(0f) }

    LaunchedEffect(featureAnnouncementId) {
        AnalyticsHelper.trackFeatureAnnouncementViewed(featureAnnouncementId)
        overlayAlpha.animateTo(1f, androidx.compose.animation.core.tween(600))
        kotlinx.coroutines.delay(200)
        phase1.animateTo(1f, ExpressiveMotion.SleekFadeSpec)
        kotlinx.coroutines.delay(100)
        phase2.animateTo(1f, ExpressiveMotion.SleekFadeSpec)
        kotlinx.coroutines.delay(100)
        phase3.animateTo(1f, ExpressiveMotion.SleekFadeSpec)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .graphicsLayer { alpha = overlayAlpha.value }
            .pointerInput(Unit) { /* Block touch-through */ }
            .padding(WindowInsets.navigationBars.asPaddingValues()),
        contentAlignment = Alignment.Center,
    ) {
        ExpressiveAnimatedBackground(
            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = phase1.value },
            ) {
                BoxLoreLogo()
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "now works with",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = phase2.value },
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_android_auto),
                    contentDescription = "Android Auto",
                    modifier = Modifier.height(140.dp),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Android Auto",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "BoxLore now plays on Android Auto. Listen hands-free while driving.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(40.dp))

            Box(modifier = Modifier.graphicsLayer { alpha = phase3.value }) {
                FilledTonalButton(
                    onClick = {
                        scope.launch { userPrefs.dismissFeatureAnnouncement(featureAnnouncementId) }
                    },
                    shape = CircleShape,
                ) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}
