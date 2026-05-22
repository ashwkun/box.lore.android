package cx.aswin.boxcast.core.model

import kotlinx.serialization.Serializable

/** A person associated with a podcast or episode (host, guest, editor, etc.) */
@Serializable
data class Person(
    val name: String,
    val role: String? = null,  // "host", "guest", "editor"
    val group: String? = null, // "cast", "writing", "visuals"
    val img: String? = null,
    val href: String? = null
)

/** A transcript resource associated with an episode */
@Serializable
data class Transcript(
    val url: String,
    val type: String  // "application/srt", "text/vtt", "application/json"
)

/** A chapter marker within an episode */
@Serializable
data class Chapter(
    val startTime: Double,  // seconds
    val title: String,
    val img: String? = null,
    val url: String? = null
)

/** A recommended podcast recommendation */
@Serializable
data class PodrollItem(
    val title: String,
    val url: String,
    val uuid: String? = null
)
