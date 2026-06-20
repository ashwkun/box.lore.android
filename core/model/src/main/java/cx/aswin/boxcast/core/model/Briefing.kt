package cx.aswin.boxcast.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Briefing(
    val date: String,
    val region: String,
    val title: String,
    val script: String,
    val audioUrl: String,
    val coverUrl: String,
    val sources: List<BriefingSource> = emptyList()
)

@Serializable
data class BriefingSource(
    val title: String,
    val url: String
)
