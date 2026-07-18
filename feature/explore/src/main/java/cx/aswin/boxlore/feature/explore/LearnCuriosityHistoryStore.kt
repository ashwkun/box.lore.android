package cx.aswin.boxlore.feature.explore

import android.app.Application
import cx.aswin.boxlore.core.data.BoxcastPrefs
import org.json.JSONArray
import org.json.JSONObject

enum class LearnHistoryAction {
    DISMISS,
    QUEUE;

    fun toStorageValue(): String = name.lowercase()

    companion object {
        fun fromStorageValue(value: String?): LearnHistoryAction =
            entries.find { it.toStorageValue() == value } ?: DISMISS
    }
}

data class LearnHistoryEntry(
    val episodeId: String,
    val episodeTitle: String,
    val podcastTitle: String?,
    val imageUrl: String?,
    val feedImage: String?,
    val podcastId: String?,
    val audioUrl: String?,
    val duration: Int,
    val description: String?,
    val question: String,
    val explanation: String?,
    val curiosityScore: Int,
    val action: LearnHistoryAction,
    val dismissedAtMs: Long
)

class LearnCuriosityHistoryStore(application: Application) {

    private val boxcastPrefs = BoxcastPrefs(application)

    fun getEntries(): List<LearnHistoryEntry> {
        val raw = boxcastPrefs.getLearnCuriosityHistoryJson() ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    parseEntry(array.getJSONObject(i))?.let(::add)
                }
            }
        }.getOrElse { emptyList() }
    }

    fun getDismissedIds(): Set<String> {
        return boxcastPrefs.getDismissedCuriosityIds()
    }

    fun recordDismissal(card: LearnCuriosityCard, action: LearnHistoryAction) {
        val episodeId = card.episodeId
        val entry = card.toHistoryEntry(action)

        val dismissed = getDismissedIds().toMutableSet()
        dismissed.add(episodeId)
        boxcastPrefs.setDismissedCuriosityIds(dismissed)

        val updated = listOf(entry) + getEntries().filterNot { it.episodeId == episodeId }
        saveEntries(updated.take(MAX_ENTRIES))
    }

    fun removeEntry(episodeId: String, queueRestore: Boolean = false): LearnHistoryEntry? {
        val entry = getEntries().find { it.episodeId == episodeId } ?: return null

        val dismissed = getDismissedIds().toMutableSet()
        dismissed.remove(episodeId)
        boxcastPrefs.setDismissedCuriosityIds(dismissed)

        saveEntries(getEntries().filterNot { it.episodeId == episodeId })

        if (queueRestore) {
            synchronized(pendingRestoreLock) {
                pendingRestores = pendingRestores + entry
            }
        }
        return entry
    }

    fun clearAll() {
        boxcastPrefs.clearLearnCuriosity()
        synchronized(pendingRestoreLock) {
            pendingRestores = emptyList()
        }
    }

    fun consumePendingRestores(): List<LearnHistoryEntry> {
        synchronized(pendingRestoreLock) {
            val copy = pendingRestores
            pendingRestores = emptyList()
            return copy
        }
    }

    private fun saveEntries(entries: List<LearnHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        boxcastPrefs.setLearnCuriosityHistoryJson(array.toString())
    }

    private fun LearnCuriosityCard.toHistoryEntry(action: LearnHistoryAction): LearnHistoryEntry {
        return LearnHistoryEntry(
            episodeId = episodeId,
            episodeTitle = episodeTitle,
            podcastTitle = podcastTitle,
            imageUrl = imageUrl,
            feedImage = feedImage,
            podcastId = podcastId,
            audioUrl = audioUrl,
            duration = duration,
            description = description,
            question = question,
            explanation = explanation,
            curiosityScore = curiosityScore,
            action = action,
            dismissedAtMs = System.currentTimeMillis()
        )
    }

    private fun LearnHistoryEntry.toJson(): JSONObject {
        return JSONObject().apply {
            put("episodeId", episodeId)
            put("episodeTitle", episodeTitle)
            put("podcastTitle", podcastTitle)
            put("imageUrl", imageUrl)
            put("feedImage", feedImage)
            put("podcastId", podcastId)
            put("audioUrl", audioUrl)
            put("duration", duration)
            put("description", description)
            put("question", question)
            put("explanation", explanation)
            put("curiosityScore", curiosityScore)
            put("action", action.toStorageValue())
            put("dismissedAtMs", dismissedAtMs)
        }
    }

    private fun parseEntry(json: JSONObject): LearnHistoryEntry? {
        val episodeId = json.optString("episodeId")
        if (episodeId.isBlank()) return null
        return LearnHistoryEntry(
            episodeId = episodeId,
            episodeTitle = json.optString("episodeTitle", "Episode"),
            podcastTitle = json.optString("podcastTitle").ifBlank { null },
            imageUrl = json.optString("imageUrl").ifBlank { null },
            feedImage = json.optString("feedImage").ifBlank { null },
            podcastId = json.optString("podcastId").ifBlank { null },
            audioUrl = json.optString("audioUrl").ifBlank { null },
            duration = json.optInt("duration", 0),
            description = json.optString("description").ifBlank { null },
            question = json.optString("question", ""),
            explanation = json.optString("explanation").ifBlank { null },
            curiosityScore = json.optInt("curiosityScore", 0),
            action = LearnHistoryAction.fromStorageValue(json.optString("action")),
            dismissedAtMs = json.optLong("dismissedAtMs", 0L)
        )
    }

    companion object {
        private const val MAX_ENTRIES = 100

        private val pendingRestoreLock = Any()
        @Volatile
        private var pendingRestores: List<LearnHistoryEntry> = emptyList()
    }
}
