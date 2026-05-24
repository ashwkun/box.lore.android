package cx.aswin.boxcast.core.network.model

import kotlinx.serialization.Serializable
import cx.aswin.boxcast.core.model.Chapter

@Serializable
data class AutoTranscriptResponse(
    val status: String, // "pending", "completed", "failed", "not_started"
    val srt: String? = null,
    val chapters: List<Chapter>? = null,
    val error: String? = null,
    val limitLeft: Int? = null
)
