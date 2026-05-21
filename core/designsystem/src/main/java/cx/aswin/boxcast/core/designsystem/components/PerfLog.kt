package cx.aswin.boxcast.core.designsystem.components

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout

@Composable
fun LogRecomposition(tag: String = "BoxCastPerf", name: String) {
    val count = remember { IntArray(1) { 0 } }
    SideEffect {
        count[0]++
        Log.d(tag, "[RECOMP] $name recomposed. Total: ${count[0]}")
    }
}

fun Modifier.logDrawTime(name: String, tag: String = "BoxCastPerf"): Modifier = this.drawWithContent {
    val startTime = System.nanoTime()
    drawContent()
    val endTime = System.nanoTime()
    val elapsedMs = (endTime - startTime) / 1_000_000.0
    // Log if drawing takes more than 1ms
    if (elapsedMs > 1.0) {
        Log.d(tag, "[DRAW] $name took ${String.format("%.2f", elapsedMs)} ms")
    }
}

fun Modifier.logLayoutTime(name: String, tag: String = "BoxCastPerf"): Modifier = this.layout { measurable, constraints ->
    val startTime = System.nanoTime()
    val placeable = measurable.measure(constraints)
    val measureTime = System.nanoTime() - startTime
    
    layout(placeable.width, placeable.height) {
        val placeStart = System.nanoTime()
        placeable.placeRelative(0, 0)
        val placeTime = System.nanoTime() - placeStart
        val totalMs = (measureTime + placeTime) / 1_000_000.0
        // Log if measure + layout takes more than 1.5ms
        if (totalMs > 1.5) {
            Log.d(tag, "[LAYOUT] $name took ${String.format("%.2f", totalMs)} ms")
        }
    }
}
