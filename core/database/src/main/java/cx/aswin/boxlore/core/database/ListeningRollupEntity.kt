package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.Index

/**
 * Compact daily per-episode summary created when raw sessions age past the retention window.
 * Keyed by local day + episode so the same show can still have fresh raw sessions today.
 */
@Entity(
    tableName = "listening_rollups",
    primaryKeys = ["localDay", "episodeId"],
    indices = [
        Index(value = ["podcastId"]),
        Index(value = ["localDay"]),
        Index(value = ["episodeId"]),
    ],
)
data class ListeningRollupEntity(
    val localDay: Long,
    val episodeId: String,
    val podcastId: String,
    val consumedMs: Long,
    val sessionCount: Int,
    val completionCount: Int,
    val lastListenedAt: Long,
    val morningMs: Long = 0L,
    val afternoonMs: Long = 0L,
    val eveningMs: Long = 0L,
    val nightMs: Long = 0L,
)
