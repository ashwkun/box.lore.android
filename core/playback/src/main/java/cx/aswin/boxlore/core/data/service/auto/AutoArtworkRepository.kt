package cx.aswin.boxlore.core.data.service.auto

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

internal object AutoArtworkRepository {
    private const val SOURCE_PREFS = "android_auto_artwork_sources"

    fun remoteUri(
        context: Context,
        remoteUrl: String?,
    ): Uri? {
        val source = remoteUrl?.takeIf { it.isNotBlank() } ?: return null
        val localFile =
            when {
                source.startsWith("file://") -> Uri.parse(source).path?.let(::File)
                File(source).isAbsolute -> File(source)
                else -> null
            }
        if (localFile != null) return localUri(context, localFile)

        val normalized = source.replaceFirst("http://", "https://")
        if (!normalized.startsWith("https://")) return null
        val key = normalized.sha256()
        context
            .getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, normalized)
            .apply()
        return Uri
            .Builder()
            .scheme("content")
            .authority("${context.packageName}.collage")
            .appendPath("art")
            .appendPath(key)
            .build()
    }

    private fun localUri(
        context: Context,
        file: File,
    ): Uri? {
        val canonicalFile = runCatching(file::getCanonicalFile).getOrNull() ?: return null
        val allowedRoots =
            listOfNotNull(
                context.cacheDir,
                context.filesDir,
                context.getExternalFilesDir(null),
                context.externalCacheDir,
            ).mapNotNull { runCatching(it::getCanonicalFile).getOrNull() }
        if (allowedRoots.none { canonicalFile.isInside(it) } || !canonicalFile.isFile) return null
        val key = canonicalFile.path.sha256()
        context
            .getSharedPreferences(SOURCE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, canonicalFile.path)
            .apply()
        return Uri
            .Builder()
            .scheme("content")
            .authority("${context.packageName}.collage")
            .appendPath("local")
            .appendPath(key)
            .build()
    }

    fun collageUri(
        context: Context,
        folderId: String,
    ): Uri? {
        val filename = "${folderId.safeFileName()}.png"
        val file = File(File(context.cacheDir, "auto_collages"), filename)
        if (!file.exists()) return null
        return Uri
            .Builder()
            .scheme("content")
            .authority("${context.packageName}.collage")
            .appendPath("collage")
            .appendPath(filename)
            .build()
    }

    private fun String.safeFileName(): String = replace(Regex("[^a-zA-Z0-9_]"), "_")

    private fun String.sha256(): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun File.isInside(root: File): Boolean = path == root.path || path.startsWith("${root.path}${File.separator}")
}
