package cx.aswin.boxlore.core.network.model

import cx.aswin.boxlore.core.model.Chapter
import kotlinx.serialization.Serializable

@Serializable
data class AutoTranscriptResponse(
    val status: String, // "pending", "completed", "failed", "not_started"
    val srt: String? = null,
    val chapters: List<Chapter>? = null,
    val error: String? = null,
    val limitLeft: Int? = null,
)
