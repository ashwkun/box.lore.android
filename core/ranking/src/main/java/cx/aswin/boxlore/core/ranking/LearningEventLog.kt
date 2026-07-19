package cx.aswin.boxlore.core.ranking

import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory, session-only audit trail of every signal that mutates the adaptive ranking model
 * and how it moved the model. Deliberately holds no persistence, does no disk/network I/O, and
 * allocates nothing while [enabled] is false, so it is safe to leave compiled into release builds.
 *
 * Mirrors the lightweight-diagnostics pattern of [RankingShadowDiagnostics]: a process-wide
 * singleton with a bounded ring buffer exposed as a [StateFlow] for live UI consumption.
 */
object LearningEventLog {

    /**
     * SharedPreferences file / key gating capture.
     * Prefer [cx.aswin.boxlore.core.prefs.BoxcastPrefs] for reads/writes; these aliases
     * stay for callers that still reference the constants.
     */
    const val PREFS_NAME = BoxcastPrefs.PREFS_NAME
    const val ENABLED_PREF_KEY = BoxcastPrefs.KEY_LEARNER_LOG_ENABLED

    private const val MAX_EVENTS = 300

    private val lock = Any()
    private val buffer = ArrayDeque<LearningEvent>(MAX_EVENTS)
    private val idSource = AtomicLong()

    @Volatile
    var enabled: Boolean = false
        private set

    private val _events = MutableStateFlow<List<LearningEvent>>(emptyList())

    /** Newest-first list of recent learning events. Empty while logging is disabled. */
    val events: StateFlow<List<LearningEvent>> = _events.asStateFlow()

    /**
     * Enable or disable capture. Disabling clears the buffer so a released user who briefly turns
     * the tool on never leaves signal history lingering in memory.
     */
    fun configure(enabled: Boolean) {
        synchronized(lock) {
            this.enabled = enabled
            if (!enabled) {
                buffer.clear()
                _events.value = emptyList()
            }
        }
    }

    fun record(build: (id: Long, timestamp: Long) -> LearningEvent) {
        // Fast path when disabled; re-check under the lock so configure(false) cannot race
        // a pending append and leave identifiers in memory after opt-out.
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            val event = build(idSource.incrementAndGet(), System.currentTimeMillis())
            buffer.addFirst(event)
            while (buffer.size > MAX_EVENTS) buffer.removeLast()
            _events.value = buffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _events.value = emptyList()
        }
    }
}

enum class LearningEventKind {
    IMPRESSION,
    ACTION,
    FACET,
    RESOLUTION,
    DUPLICATE,
    PRUNE,
}

/**
 * A single thing that touched the learning engine. [title] and [effect] are pre-formatted,
 * human-readable strings so the debug UI can render a feed without re-deriving wording. [reward]
 * (when present) is the signed reward that drove the event, used purely for colour cues.
 */
sealed interface LearningEvent {
    val id: Long
    val timestamp: Long
    val kind: LearningEventKind
    val title: String
    val effect: String
    val reward: Double?

    /** An impression was shown to the user and is now awaiting an outcome. */
    data class Impression(
        override val id: Long,
        override val timestamp: Long,
        val episodeId: String,
        val podcastId: String,
        val objective: String,
        val surface: String,
        val source: String,
        val entryPoint: String?,
    ) : LearningEvent {
        override val kind = LearningEventKind.IMPRESSION
        override val reward: Double? = null
        override val title: String = "Impression shown"
        override val effect: String = buildString {
            append(prettyToken(surface))
            append(" · ")
            append(prettyToken(source))
            entryPoint?.let {
                append(" · ")
                append(prettyEntryPoint(it))
            }
        }
    }

    /** A user action was received and turned into a reward. */
    data class ActionReceived(
        override val id: Long,
        override val timestamp: Long,
        val action: RankingAction,
        override val reward: Double?,
        val podcastId: String,
        val genre: String?,
        val source: String?,
        val listenSeconds: Long,
    ) : LearningEvent {
        override val kind = LearningEventKind.ACTION
        override val title: String = prettyAction(action)
        override val effect: String = buildString {
            append("reward ")
            append(signed(reward ?: 0.0))
            genre?.let {
                append(" · ")
                append(prettyToken(it))
            }
            if (listenSeconds > 0) {
                append(" · ")
                append(formatSeconds(listenSeconds))
            }
        }
    }

    /** A taste facet's evidence and affinity moved. */
    data class FacetUpdated(
        override val id: Long,
        override val timestamp: Long,
        val facetType: PreferenceFacetType,
        val key: String,
        override val reward: Double?,
        val affinityBefore: Double,
        val affinityAfter: Double,
        val positiveBefore: Double,
        val positiveAfter: Double,
        val negativeBefore: Double,
        val negativeAfter: Double,
    ) : LearningEvent {
        override val kind = LearningEventKind.FACET
        val affinityDelta: Double = affinityAfter - affinityBefore
        override val title: String = "Taste · ${facetType.name.lowercase()}"
        override val effect: String = buildString {
            append(prettyToken(key))
            append("  ")
            append(format2(affinityBefore))
            append(" → ")
            append(format2(affinityAfter))
            append(" (")
            append(signed(affinityDelta))
            append(")")
        }
    }

    /** An impression was resolved into a bandit update (or no matching impression was found). */
    data class ExposureResolved(
        override val id: Long,
        override val timestamp: Long,
        val objective: String,
        val matched: Boolean,
        val entryPoint: String?,
        val surface: String?,
        val source: String?,
        val exposureAgeMillis: Long?,
        override val reward: Double?,
        val updateCountBefore: Long,
        val updateCountAfter: Long,
        val blendBefore: Double,
        val blendAfter: Double,
    ) : LearningEvent {
        override val kind = LearningEventKind.RESOLUTION
        override val title: String = if (matched) "Model update · ${prettyToken(objective)}" else "No impression matched"
        override val effect: String = if (matched) {
            buildString {
                append("#")
                append(updateCountBefore)
                append(" → #")
                append(updateCountAfter)
                append(" · blend ")
                append(percent(blendBefore))
                append(" → ")
                append(percent(blendAfter))
                val where = entryPoint?.let { prettyEntryPoint(it) } ?: surface?.let { prettyToken(it) }
                where?.let {
                    append(" · ")
                    append(it)
                }
                exposureAgeMillis?.let {
                    append(" · after ")
                    append(formatDuration(it))
                }
            }
        } else {
            "reward ${signed(reward ?: 0.0)} had no waiting impression to attach to"
        }
    }

    /** A duplicate action inside the dedup window was dropped before it could affect anything. */
    data class DuplicateIgnored(
        override val id: Long,
        override val timestamp: Long,
        val action: RankingAction,
        val episodeId: String,
    ) : LearningEvent {
        override val kind = LearningEventKind.DUPLICATE
        override val reward: Double? = null
        override val title: String = "Ignored duplicate"
        override val effect: String = "${prettyAction(action)} repeated within 5s window"
    }

    /** Genre facets were canonicalized: aliases merged, placeholders dropped. */
    data class GenreFacetPruned(
        override val id: Long,
        override val timestamp: Long,
        val mergedCount: Int,
        val removedCount: Int,
    ) : LearningEvent {
        override val kind = LearningEventKind.PRUNE
        override val reward: Double? = null
        override val title: String = "Genre facets cleaned"
        override val effect: String = "$mergedCount merged · $removedCount dropped"
    }

    companion object {
        internal fun prettyAction(action: RankingAction): String =
            action.name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

        internal fun prettyToken(raw: String): String =
            raw.trim().lowercase().replace('_', ' ').replaceFirstChar(Char::titlecase)

        internal fun prettyEntryPoint(raw: String): String =
            raw.removePrefix("home_adaptive_").replace('_', ' ').trim()

        internal fun signed(value: Double): String =
            (if (value >= 0) "+" else "") + format2(value)

        internal fun format2(value: Double): String = String.format(Locale.US, "%.2f", value)

        internal fun percent(value: Double): String =
            String.format(Locale.US, "%d%%", (value * 100).toInt())

        internal fun formatSeconds(seconds: Long): String = when {
            seconds < 60 -> "${seconds}s"
            else -> "${seconds / 60}m ${seconds % 60}s"
        }

        internal fun relativeAge(ageMillis: Long): String {
            val minutes = ageMillis / 60_000L
            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> "${minutes}m ago"
                minutes < 24 * 60 -> "${minutes / 60}h ago"
                else -> "${minutes / (24 * 60)}d ago"
            }
        }

        /** Formats a latency/duration (not event recency). */
        internal fun formatDuration(durationMillis: Long): String {
            val minutes = durationMillis / 60_000L
            return when {
                minutes < 1 -> "<1m"
                minutes < 60 -> "${minutes}m"
                minutes < 24 * 60 -> "${minutes / 60}h"
                else -> "${minutes / (24 * 60)}d"
            }
        }
    }
}
