package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One closed listening session with naturally consumed audio milliseconds.
 * Primary key is a stable UUID so backup restore can upsert without duplicating.
 */
@Entity(
    tableName = "listening_sessions",
    indices = [
        Index(value = ["endedAt"]),
        Index(value = ["episodeId"]),
        Index(value = ["podcastId"]),
        Index(value = ["localDay"]),
    ],
)
data class ListeningSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val episodeId: String,
    val podcastId: String,
    val startedAt: Long,
    val endedAt: Long,
    /** Seek-excluding consumed audio from PlaybackTelemetrySession. */
    val consumedMs: Long,
    val completed: Boolean,
    /** Local calendar day as epoch day (ZoneId.systemDefault). */
    val localDay: Long,
    /** 0=morning, 1=afternoon, 2=evening, 3=night */
    val timeBucket: Int,
)
