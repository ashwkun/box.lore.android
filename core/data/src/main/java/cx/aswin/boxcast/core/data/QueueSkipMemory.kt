package cx.aswin.boxcast.core.data

import android.content.Context
import android.util.Log

/**
 * Lightweight local memory of auto-filled episodes the user rejected — either removed
 * from the queue sheet or skipped within the first 30 seconds of playback.
 *
 * The SmartQueueEngine uses this to:
 *  - never re-suggest a skipped episode, and
 *  - down-rank podcasts that accumulated 2+ recent skips in fallback tiers.
 *
 * Entries expire after 7 days and the store is capped at 200 entries.
 *
 * Persistence is injected as read/write lambdas so the class is a pure JVM type
 * (unit-testable without Android). Use [fromContext] in production code.
 */
class QueueSkipMemory(
    private val readRaw: () -> String?,
    private val writeRaw: (String) -> Unit,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    data class SkipEntry(
        val episodeId: String,
        val podcastId: String,
        val source: String,
        val timestampMs: Long
    )

    companion object {
        const val MAX_ENTRIES = 200
        const val EXPIRY_MS = 7L * 24 * 60 * 60 * 1000
        const val DOWN_RANK_MIN_SKIPS = 2
        private const val FIELD_SEPARATOR = "|"
        private const val ENTRY_SEPARATOR = "\n"
        private const val PREFS_NAME = "queue_skip_memory"
        private const val KEY_ENTRIES = "entries"
        private const val TAG = "QueueSkipMemory"

        fun fromContext(context: Context): QueueSkipMemory {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return QueueSkipMemory(
                readRaw = { prefs.getString(KEY_ENTRIES, null) },
                writeRaw = { prefs.edit().putString(KEY_ENTRIES, it).apply() }
            )
        }
    }

    @Synchronized
    fun recordSkip(episodeId: String, podcastId: String?, source: String?) {
        if (episodeId.isBlank()) return
        val entries = load().toMutableList()
        entries.removeAll { it.episodeId == episodeId }
        entries.add(
            SkipEntry(
                episodeId = episodeId,
                podcastId = podcastId.orEmpty(),
                source = source.orEmpty(),
                timestampMs = nowMs()
            )
        )
        save(pruneList(entries))
    }

    /** Episode IDs the user recently rejected — never re-suggest these. */
    @Synchronized
    fun skippedEpisodeIds(): Set<String> = pruneList(load()).map { it.episodeId }.toSet()

    /** Podcasts with [DOWN_RANK_MIN_SKIPS]+ recent skips — de-prioritized in fallback tiers. */
    @Synchronized
    fun downRankedPodcastIds(): Set<String> {
        return pruneList(load())
            .filter { it.podcastId.isNotBlank() }
            .groupingBy { it.podcastId }
            .eachCount()
            .filterValues { it >= DOWN_RANK_MIN_SKIPS }
            .keys
    }

    /** Removes expired entries from the persisted store. */
    @Synchronized
    fun prune() {
        val entries = load()
        val pruned = pruneList(entries)
        if (pruned.size != entries.size) save(pruned)
    }

    private fun pruneList(entries: List<SkipEntry>): List<SkipEntry> {
        val cutoff = nowMs() - EXPIRY_MS
        return entries
            .filter { it.timestampMs >= cutoff }
            .sortedBy { it.timestampMs }
            .takeLast(MAX_ENTRIES)
    }

    private fun load(): List<SkipEntry> {
        val raw = try {
            readRaw()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read skip memory", e)
            null
        } ?: return emptyList()
        return raw.split(ENTRY_SEPARATOR).mapNotNull { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size < 4) return@mapNotNull null
            val ts = parts[3].toLongOrNull() ?: return@mapNotNull null
            SkipEntry(
                episodeId = parts[0],
                podcastId = parts[1],
                source = parts[2],
                timestampMs = ts
            )
        }
    }

    private fun save(entries: List<SkipEntry>) {
        val sanitized = entries.joinToString(ENTRY_SEPARATOR) { entry ->
            listOf(entry.episodeId, entry.podcastId, entry.source, entry.timestampMs.toString())
                .joinToString(FIELD_SEPARATOR) { it.replace(FIELD_SEPARATOR, "_").replace(ENTRY_SEPARATOR, " ") }
        }
        try {
            writeRaw(sanitized)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist skip memory", e)
        }
    }
}
