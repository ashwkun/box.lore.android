package cx.aswin.boxcast.feature.home.settings.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxcast.core.designsystem.theme.generateBrandColorScheme
import cx.aswin.boxcast.core.designsystem.theme.toThemeBrandHex
import kotlinx.coroutines.delay

/** Debounce before recomputing the harmonized Material 3 preview while dragging. */
private const val HarmonizedPreviewDebounceMs = 120L

@Composable
internal fun AccentColorPickerDialog(
    initialColor: Color,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialColor) { colorToHsv(initialColor) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val selectedColor = remember(hue, saturation, value) {
        hsvToColor(hue, saturation, value)
    }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // generateBrandColorScheme is too expensive to run on every drag frame; debounce it
    // and only refresh the harmonized preview once the user pauses (or releases).
    var matchedPrimary by remember {
        mutableStateOf(
            generateBrandColorScheme(
                seedColor = selectedColor,
                isDark = isDark,
                surfaceStyle = SurfaceStyles.STANDARD,
            ).primary,
        )
    }
    LaunchedEffect(selectedColor, isDark) {
        delay(HarmonizedPreviewDebounceMs)
        matchedPrimary = generateBrandColorScheme(
            seedColor = selectedColor,
            isDark = isDark,
            surfaceStyle = SurfaceStyles.STANDARD,
        ).primary
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom accent") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Your color is used as a seed and matched to the closest Material 3 palette.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorSwatchPreview(
                        label = "Seed",
                        color = selectedColor,
                        hex = selectedColor.toThemeBrandHex(),
                    )
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ColorSwatchPreview(
                        label = "Material 3",
                        color = matchedPrimary,
                        hex = matchedPrimary.toThemeBrandHex(),
                    )
                }
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v ->
                        saturation = s
                        value = v
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f),
                )
                HueBar(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedColor.toThemeBrandHex()) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ColorSwatchPreview(
    label: String,
    color: Color,
    hex: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = color,
            shadowElevation = 2.dp,
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = hex,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pointerInput(hue) {
                detectTapGestures { offset ->
                    onChange(
                        (offset.x / size.width).coerceIn(0f, 1f),
                        1f - (offset.y / size.height).coerceIn(0f, 1f),
                    )
                }
            }
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        1f - (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hueColor = hsvToColor(hue, 1f, 1f)
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val indicator = Offset(
                x = saturation * size.width,
                y = (1f - value) * size.height,
            )
            drawCircle(
                color = Color.White,
                radius = 14f,
                center = indicator,
                style = Stroke(width = 4f),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = 14f,
                center = indicator,
                style = Stroke(width = 1.5f),
            )
        }
    }
}

@Composable
private fun HueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hues = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red,
        )
    }
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(brush = Brush.horizontalGradient(hues))
            val x = (hue / 360f) * size.width
            drawCircle(
                color = Color.White,
                radius = size.height / 2f,
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 3f),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = size.height / 2f,
                center = Offset(x, size.height / 2f),
                style = Stroke(width = 1.5f),
            )
        }
    }
}

private fun colorToHsv(color: Color): FloatArray {
    val hsvOut = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        ),
        hsvOut,
    )
    return hsvOut
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    return Color(colorInt)
}
