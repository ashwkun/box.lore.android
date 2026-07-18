package cx.aswin.boxlore.feature.explore.logic

import java.security.SecureRandom
import java.util.Random
import kotlin.math.pow

internal fun <T> filterAndShuffleNewItems(
    rawItems: List<T>,
    currentStack: List<T>,
    dismissedIds: Set<String>,
    episodeId: (T) -> String,
    curiosityScore: (T) -> Int,
    random: Random = SecureRandom(),
): List<T> {
    val newItems = rawItems.filterNot { episodeId(it) in dismissedIds }
    if (newItems.isEmpty()) return emptyList()

    val shuffledNew = weightedShuffle(
        list = newItems,
        curiosityScore = curiosityScore,
        random = random,
    )
    val existingIds = currentStack.map(episodeId).toSet()
    return shuffledNew.filterNot { episodeId(it) in existingIds }
}

internal fun <T> weightedShuffle(
    list: List<T>,
    curiosityScore: (T) -> Int,
    random: Random = SecureRandom(),
): List<T> {
    if (list.size <= 1) return list
    return list.map { item ->
        val u = random.nextDouble()
        val w = curiosityScore(item).toDouble() + 1.0
        item to u.pow(1.0 / w)
    }.sortedByDescending { it.second }
        .map { it.first }
}
