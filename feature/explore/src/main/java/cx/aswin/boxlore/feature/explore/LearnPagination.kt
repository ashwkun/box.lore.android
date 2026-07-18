package cx.aswin.boxlore.feature.explore

internal sealed interface InitialCuriosityDeckResult {
    data class Found(
        val page: Int,
        val unseenItems: List<LearnCuriosityCard>
    ) : InitialCuriosityDeckResult

    data class Exhausted(val lastPage: Int) : InitialCuriosityDeckResult

    data class Failed(val page: Int) : InitialCuriosityDeckResult
}

internal suspend fun findFirstUnseenCuriosityDeck(
    dismissedIds: Set<String>,
    startPage: Int = 1,
    maxAttempts: Int = 5,
    fetchPage: suspend (Int) -> List<LearnCuriosityCard>?
): InitialCuriosityDeckResult {
    val firstPage = startPage.coerceAtLeast(1)
    val attempts = maxAttempts.coerceAtLeast(1)
    var page = firstPage

    repeat(attempts) {
        val questionsStack = fetchPage(page) ?: return InitialCuriosityDeckResult.Failed(page)
        if (questionsStack.isEmpty()) {
            return InitialCuriosityDeckResult.Exhausted(page)
        }

        val unseenItems = questionsStack.filterNot {
            it.episodeId in dismissedIds
        }
        if (unseenItems.isNotEmpty()) {
            return InitialCuriosityDeckResult.Found(page, unseenItems)
        }
        page++
    }

    return InitialCuriosityDeckResult.Exhausted(page - 1)
}
