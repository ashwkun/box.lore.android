package cx.aswin.boxcast.core.model

object ShareLinkBuilder {
    private const val BASE_URL = "https://aswin.cx/boxlore/share"

    fun podcast(id: String): String = "$BASE_URL?type=podcast&id=$id"

    fun episode(
        id: String,
        timestampMs: Long? = null,
        startMs: Long? = null,
        endMs: Long? = null
    ): String {
        val baseUrl = "$BASE_URL?type=episode&id=$id"
        return when {
            startMs != null && endMs != null -> {
                "$baseUrl&start=${startMs / 1000}&end=${endMs / 1000}"
            }
            timestampMs != null && timestampMs > 0 -> {
                "$baseUrl&t=${timestampMs / 1000}"
            }
            else -> baseUrl
        }
    }

    fun build(
        type: String,
        id: String,
        timestampMs: Long? = null,
        startMs: Long? = null,
        endMs: Long? = null
    ): String = if (type == "podcast") {
        podcast(id)
    } else {
        episode(
            id = id,
            timestampMs = timestampMs,
            startMs = startMs,
            endMs = endMs
        )
    }
}
