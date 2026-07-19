package cx.aswin.boxlore.core.data

/**
 * Pure index-mapping and queue-type helpers shared by the player repository, the
 * playback service, and the queue UI. Kept free of Android dependencies so the
 * mapping rules (learn-prefix stripping, hidden-current offsets, Lore detection)
 * are unit-testable.
 */
object QueueMath {
    const val LEARN_PREFIX = "learn:"
    const val EPISODE_PREFIX = "episode:"
    const val QUEUE_PREFIX = "queue:"
    const val CONTEXT_TYPE_LORE = "LORE"

    /** Strips any media-id prefix so ids can be compared against raw episode ids. */
    fun stripMediaIdPrefixes(mediaId: String): String =
        mediaId.removePrefix(LEARN_PREFIX).removePrefix(EPISODE_PREFIX).removePrefix(QUEUE_PREFIX)

    /** Finds the media-item index of an episode, matching by id with prefixes stripped. */
    fun mediaIndexOfEpisode(mediaIds: List<String>, episodeId: String): Int =
        mediaIds.indexOfFirst { stripMediaIdPrefixes(it) == episodeId }

    /**
     * The queue sheet hides the currently playing item (queue index 0), so UI list
     * indices are offset by one from PlayerState.queue indices.
     */
    fun uiIndexToQueueIndex(uiIndex: Int): Int = uiIndex + 1

    fun queueIndexToUiIndex(queueIndex: Int): Int = queueIndex - 1

    /**
     * A queue is a "normal" queue (as opposed to a Lore-only queue) when any media id
     * is not learn-prefixed.
     */
    fun hasNonLoreMediaIds(mediaIds: List<String>): Boolean =
        mediaIds.any { !it.startsWith(LEARN_PREFIX) }

    /** Same detection, from persisted queue rows (survives process restarts). */
    fun hasNonLoreContextTypes(contextTypes: List<String?>): Boolean =
        contextTypes.any { it != CONTEXT_TYPE_LORE }

    /** Moves an element within a list, returning a new list. */
    fun <T> moveItem(list: List<T>, fromIndex: Int, toIndex: Int): List<T> {
        if (fromIndex == toIndex) return list
        if (fromIndex !in list.indices || toIndex !in list.indices) return list
        val mutable = list.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return mutable
    }
}
