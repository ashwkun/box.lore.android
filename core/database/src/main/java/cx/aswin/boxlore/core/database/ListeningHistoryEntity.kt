package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_history")
data class ListeningHistoryEntity(
    @PrimaryKey
    val episodeId: String,
    val podcastId: String,
    
    // Cached Metadata (to avoid API calls for display)
    val episodeTitle: String,
    val episodeImageUrl: String?,
    val podcastImageUrl: String?, // Fallback image if episode image fails
    val episodeAudioUrl: String?,
    val podcastName: String,
    
    // Playback State
    val progressMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val isLiked: Boolean = false, // New: User "Like" status
    val lastPlayedAt: Long,
    
    // Sync Status
    val isDirty: Boolean = true, // Needs to be pushed to Supabase
    val syncedAt: Long = 0,
    val enclosureType: String? = null,
    val isManualCompletion: Boolean = false,
    val isBulkCompletion: Boolean = false,
    val episodeDescription: String? = null
)
