package cx.aswin.boxlore.core.playback

/**
 * Builds aligned collage image URL + content-key lists for Android Auto folder tiles.
 * Keys and images always come from the same filtered entries so signatures match pixels.
 */
object AutoCollageFolderLogic {
    data class ArtPair(
        val contentKey: String,
        val imageUrl: String,
    )

    fun takeAligned(
        pairs: List<ArtPair>,
        limit: Int = 4,
    ): List<ArtPair> = pairs.filter { it.contentKey.isNotBlank() && it.imageUrl.isNotBlank() }.take(limit)

    fun imagesOf(pairs: List<ArtPair>): List<String> = pairs.map { it.imageUrl }

    fun keysOf(pairs: List<ArtPair>): List<String> = pairs.map { it.contentKey }

    fun pairOrNull(
        contentKey: String?,
        imageUrl: String?,
    ): ArtPair? {
        val key = contentKey?.takeIf { it.isNotBlank() } ?: return null
        val image = imageUrl?.takeIf { it.isNotBlank() } ?: return null
        return ArtPair(contentKey = key, imageUrl = image)
    }
}
