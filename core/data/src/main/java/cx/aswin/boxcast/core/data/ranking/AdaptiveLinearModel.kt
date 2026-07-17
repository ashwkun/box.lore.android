package cx.aswin.boxcast.core.data.ranking

import kotlin.math.sqrt
import kotlin.math.tanh

data class AdaptiveModelState(
    val featureSchemaVersion: Int = RankingFeatureSchema.VERSION,
    val dimension: Int = RankingFeatureSchema.dimension,
    val covariance: DoubleArray = identityMatrix(RankingFeatureSchema.dimension, RIDGE),
    val inverseCovariance: DoubleArray = identityMatrix(
        RankingFeatureSchema.dimension,
        1.0 / RIDGE,
    ),
    val rewardVector: DoubleArray = DoubleArray(RankingFeatureSchema.dimension),
    val updateCount: Long = 0,
) {
    init {
        require(dimension > 0)
        require(covariance.size == dimension * dimension)
        require(inverseCovariance.size == dimension * dimension)
        require(rewardVector.size == dimension)
    }

    companion object {
        const val RIDGE = 1.0
    }
}

class AdaptiveLinearModel(
    private val forgettingFactor: Double = 0.995,
    private val explorationAlpha: Double = 0.15,
    private val explorationThreshold: Long = 50,
    private val maximumLearnedBlend: Double = 0.65,
) {
    init {
        require(forgettingFactor in 0.9..1.0)
        require(explorationAlpha >= 0.0)
        require(explorationThreshold > 0)
        require(maximumLearnedBlend in 0.0..1.0)
    }

    fun score(
        objective: RankingObjective,
        features: RankingFeatures,
        priorScore: Double,
        state: AdaptiveModelState,
    ): RankingScore {
        requireCompatible(features, state)
        val theta = multiply(state.inverseCovariance, state.rewardVector, state.dimension)
        val rawLearned = dot(theta, features.values)
        val learned = tanh(rawLearned)
        val uncertainty = if (objective.allowsExploration && state.updateCount >= explorationThreshold) {
            val projected = multiply(state.inverseCovariance, features.values, state.dimension)
            explorationAlpha * sqrt(dot(features.values, projected).coerceAtLeast(0.0))
        } else {
            0.0
        }
        val blend = (state.updateCount.toDouble() / explorationThreshold)
            .coerceIn(0.0, 1.0) * maximumLearnedBlend
        val boundedPrior = priorScore.coerceIn(-1.0, 1.0)
        val final = ((1.0 - blend) * boundedPrior + blend * learned + uncertainty)
            .coerceIn(-1.0, 1.0)
        return RankingScore(
            finalScore = final,
            priorScore = boundedPrior,
            learnedScore = learned,
            explorationBonus = uncertainty,
            learnedBlend = blend,
            updateCount = state.updateCount,
            contributions = lazy(LazyThreadSafetyMode.NONE) {
                FeatureSlot.entries.associateWith { slot ->
                    theta[slot.ordinal] * features.values[slot.ordinal]
                }
            },
        )
    }

    /** The prior→learned blend weight for a given number of resolved outcomes. */
    fun learnedBlend(updateCount: Long): Double =
        (updateCount.toDouble() / explorationThreshold).coerceIn(0.0, 1.0) * maximumLearnedBlend

    fun update(
        features: RankingFeatures,
        reward: Double,
        state: AdaptiveModelState,
    ): AdaptiveModelState {
        requireCompatible(features, state)
        val boundedReward = reward.coerceIn(-1.0, 1.0)
        val dimension = state.dimension
        val covariance = DoubleArray(state.covariance.size)
        for (row in 0 until dimension) {
            for (column in 0 until dimension) {
                val index = row * dimension + column
                val retained = state.covariance[index] * forgettingFactor
                val ridgeRefresh = if (row == column) {
                    (1.0 - forgettingFactor) * AdaptiveModelState.RIDGE
                } else {
                    0.0
                }
                covariance[index] = retained +
                    ridgeRefresh +
                    features.values[row] * features.values[column]
            }
        }
        val rewardVector = DoubleArray(dimension) { index ->
            state.rewardVector[index] * forgettingFactor + features.values[index] * boundedReward
        }
        val inverse = invertPositiveDefinite(covariance, dimension)
        return AdaptiveModelState(
            featureSchemaVersion = state.featureSchemaVersion,
            dimension = dimension,
            covariance = covariance,
            inverseCovariance = inverse,
            rewardVector = rewardVector,
            updateCount = state.updateCount + 1,
        )
    }

    private fun requireCompatible(features: RankingFeatures, state: AdaptiveModelState) {
        require(features.schemaVersion == state.featureSchemaVersion) {
            "Feature schema ${features.schemaVersion} does not match model ${state.featureSchemaVersion}"
        }
        require(features.values.size == state.dimension)
    }
}

internal fun identityMatrix(dimension: Int, diagonal: Double): DoubleArray {
    return DoubleArray(dimension * dimension) { index ->
        if (index / dimension == index % dimension) diagonal else 0.0
    }
}

internal fun dot(left: DoubleArray, right: DoubleArray): Double {
    require(left.size == right.size)
    return left.indices.sumOf { index -> left[index] * right[index] }
}

internal fun multiply(matrix: DoubleArray, vector: DoubleArray, dimension: Int): DoubleArray {
    require(matrix.size == dimension * dimension)
    require(vector.size == dimension)
    return DoubleArray(dimension) { row ->
        var sum = 0.0
        for (column in 0 until dimension) {
            sum += matrix[row * dimension + column] * vector[column]
        }
        sum
    }
}

internal fun invertPositiveDefinite(matrix: DoubleArray, dimension: Int): DoubleArray {
    require(matrix.size == dimension * dimension)
    val augmented = augmentedWithIdentity(matrix, dimension)
    for (pivotIndex in 0 until dimension) {
        swapBestPivotIntoPlace(augmented, pivotIndex)
        normalizePivotRow(augmented, pivotIndex)
        eliminatePivotColumn(augmented, pivotIndex)
    }
    return DoubleArray(dimension * dimension) { index ->
        val row = index / dimension
        val column = index % dimension
        augmented[row][column + dimension]
    }
}

private fun augmentedWithIdentity(
    matrix: DoubleArray,
    dimension: Int,
): Array<DoubleArray> {
    return Array(dimension) { row ->
        DoubleArray(dimension * 2) { column ->
            when {
                column < dimension -> matrix[row * dimension + column]
                column - dimension == row -> 1.0
                else -> 0.0
            }
        }
    }
}

private fun swapBestPivotIntoPlace(
    augmented: Array<DoubleArray>,
    pivotIndex: Int,
) {
    val bestRow = (pivotIndex until augmented.size).maxBy { row ->
        kotlin.math.abs(augmented[row][pivotIndex])
    }
    if (bestRow == pivotIndex) return
    val temporary = augmented[pivotIndex]
    augmented[pivotIndex] = augmented[bestRow]
    augmented[bestRow] = temporary
}

private fun normalizePivotRow(
    augmented: Array<DoubleArray>,
    pivotIndex: Int,
) {
    val pivot = augmented[pivotIndex][pivotIndex]
    require(kotlin.math.abs(pivot) > 1e-12) { "Ranking covariance matrix is singular" }
    for (column in augmented[pivotIndex].indices) {
        augmented[pivotIndex][column] /= pivot
    }
}

private fun eliminatePivotColumn(
    augmented: Array<DoubleArray>,
    pivotIndex: Int,
) {
    for (row in augmented.indices) {
        if (row != pivotIndex) eliminateFromRow(augmented, row, pivotIndex)
    }
}

private fun eliminateFromRow(
    augmented: Array<DoubleArray>,
    row: Int,
    pivotIndex: Int,
) {
    val factor = augmented[row][pivotIndex]
    if (factor == 0.0) return
    for (column in augmented[row].indices) {
        augmented[row][column] -= factor * augmented[pivotIndex][column]
    }
}
