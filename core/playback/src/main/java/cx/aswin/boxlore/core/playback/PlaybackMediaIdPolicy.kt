package cx.aswin.boxlore.core.playback

import android.os.Bundle
import cx.aswin.boxlore.core.playback.QueueMath
import cx.aswin.boxlore.core.model.PlaybackEntryPoint

/**
 * Pure media-id encoding and entry-point parsing for Media3 playlists.
 * Extracted from [cx.aswin.boxlore.core.playback.PlaybackRepository].
 */
object PlaybackMediaIdPolicy {
    fun stripMediaIdPrefixes(mediaId: String): String = QueueMath.stripMediaIdPrefixes(mediaId)

    fun encodeMediaId(
        episodeId: String,
        useLearnPrefix: Boolean,
    ): String = if (useLearnPrefix) "${QueueMath.LEARN_PREFIX}$episodeId" else episodeId

    fun isLearnEntryPoint(entryPoint: String?): Boolean = entryPoint == "learn"

    fun isLearnEntryPoint(entryPoint: PlaybackEntryPoint): Boolean = entryPoint == PlaybackEntryPoint.LEARN

    fun parseEntryPointString(
        entrypoint: String?,
        entryPointLegacy: String?,
    ): String? = entrypoint ?: entryPointLegacy

    fun parseEntryPointString(bundle: Bundle?): String? =
        parseEntryPointString(
            entrypoint = bundle?.getString("entrypoint"),
            entryPointLegacy = bundle?.getString("entry_point"),
        )
}
