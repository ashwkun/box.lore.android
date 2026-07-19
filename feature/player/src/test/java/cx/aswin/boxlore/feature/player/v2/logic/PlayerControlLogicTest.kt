package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerControlLogicTest {
    private val weights = listOf(0.29f, 0.42f, 0.29f)

    @Test
    fun inactiveControlsKeepBaseWeights() {
        weights.indices.forEach { index ->
            assertEquals(weights[index], targetControlWeight(index, null, weights), 0.0001f)
        }
    }

    @Test
    fun activeControlGrowsAndNeighborsShrink() {
        val result = weights.indices.map { targetControlWeight(it, 1, weights) }

        assertTrue(result[1] > weights[1])
        assertTrue(result[0] < weights[0])
        assertTrue(result[2] < weights[2])
        assertEquals(result[0], result[2], 0.0001f)
    }

    @Test
    fun redistributionPreservesTotalWidth() {
        weights.indices.forEach { active ->
            val total =
                weights.indices
                    .sumOf {
                        targetControlWeight(it, active, weights).toDouble()
                    }.toFloat()
            assertEquals(weights.sum(), total, 0.0001f)
        }
    }

    @Test
    fun redistributedWeightsRemainPositive() {
        weights.indices.forEach { active ->
            weights.indices.forEach { index ->
                assertTrue(targetControlWeight(index, active, weights) > 0f)
            }
        }
    }

    @Test
    fun downloadLabelsPrioritizeActiveDownload() {
        assertEquals("Download", downloadLabel(isDownloaded = false, isDownloading = false))
        assertEquals("Saved", downloadLabel(isDownloaded = true, isDownloading = false))
        assertEquals("Saving", downloadLabel(isDownloaded = false, isDownloading = true))
        assertEquals("Saving", downloadLabel(isDownloaded = true, isDownloading = true))
    }

    @Test
    fun speedLabelsPreserveExistingDisplayFormat() {
        assertEquals("1×", formatSpeedLabel(1f))
        assertEquals("2×", formatSpeedLabel(2f))
        assertEquals("0.8×", formatSpeedLabel(0.8f))
        assertEquals("1.25×", formatSpeedLabel(1.25f))
        assertEquals("1.5×", formatSpeedLabel(1.5f))
    }
}
