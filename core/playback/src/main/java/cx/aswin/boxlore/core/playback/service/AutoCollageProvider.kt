package cx.aswin.boxlore.core.playback.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import cx.aswin.boxlore.core.playback.AutoArtworkFetchLogic
import cx.aswin.boxlore.core.playback.service.auto.AutoArtworkSourceStore
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-only artwork bridge for the Android Auto host process.
 *
 * Folder collages are pre-generated. Remote cover URLs are registered by
 * [cx.aswin.boxlore.core.playback.service.auto.AutoArtworkRepository] and fetched
 * lazily into a bounded app-cache location when the host opens the URI.
 */
open class AutoCollageProvider : ContentProvider() {
    companion object {
        private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
        private const val MAX_CACHE_FILES = 200
        private val artworkLocks = ConcurrentHashMap<String, Any>()
        private val HEX_64_PATTERN = Regex("[a-f0-9]{64}")

        fun getUri(
            context: android.content.Context,
            filename: String,
            version: String? = null,
        ): Uri {
            val builder =
                Uri
                    .Builder()
                    .scheme("content")
                    .authority("${context.packageName}.collage")
                    .appendPath("collage")
                    .appendPath(filename)
            if (!version.isNullOrBlank()) {
                builder.appendQueryParameter("v", version)
            }
            return builder.build()
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? {
        if (mode != "r") return null
        val context = context ?: return null
        val segments = uri.pathSegments
        if (segments.size != 2) return null
        val kind = segments[0]
        val key = segments[1]
        if (kind == "art") return openRemoteArtwork(uri, context, key)
        val file =
            when (kind) {
                "collage" -> resolveCollage(context, key)
                "local" -> resolveRegisteredLocalFile(context, key)
                else -> null
            } ?: return null

        if (!file.isFile) {
            android.util.Log.w("CollageProvider", "File not found: ${file.absolutePath}")
            return null
        }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = if (uri.pathSegments.firstOrNull() == "collage") "image/png" else "image/*"

    private fun openRemoteArtwork(
        uri: Uri,
        context: android.content.Context,
        key: String,
    ): ParcelFileDescriptor? {
        val cached = cachedRemoteArtwork(context, key)
        if (cached != null) {
            return ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        if (!key.matches(HEX_64_PATTERN)) return null
        return openPipeHelper(
            uri,
            "image/*",
            null,
            key,
            ContentProvider.PipeDataWriter<String> { output, _, _, _, pipeKey ->
                pipeKey?.let { registeredKey ->
                    getOrFetchRemoteArtwork(context, registeredKey)?.inputStream()?.use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(output).use { pipe ->
                            input.copyTo(pipe)
                        }
                    }
                }
            },
        )
    }

    private fun cachedRemoteArtwork(
        context: android.content.Context,
        key: String,
    ): File? {
        if (!key.matches(HEX_64_PATTERN)) return null
        val cacheDir = File(context.cacheDir, "auto_artwork").apply { mkdirs() }
        return childFile(cacheDir, key)
            ?.takeIf { candidate ->
                if (!candidate.isFile) return@takeIf false
                val valid =
                    candidate.length() in 1..AutoArtworkFetchLogic.MAX_ARTWORK_BYTES &&
                        AutoArtworkFetchLogic.looksLikeImage(candidate.readHeader(12))
                if (!valid) {
                    // Drop corrupt / non-image leftovers so the next open retries the CDN.
                    candidate.delete()
                }
                valid
            }
    }

    private fun getOrFetchRemoteArtwork(
        context: android.content.Context,
        key: String,
    ): File? {
        if (!key.matches(HEX_64_PATTERN)) return null
        val cacheDir = File(context.cacheDir, "auto_artwork").apply { mkdirs() }
        val target = childFile(cacheDir, key) ?: return null
        cachedRemoteArtwork(context, key)?.let { return it }

        val source =
            AutoArtworkSourceStore.get(context, key)
                ?: return null
        val sourceUrl =
            AutoArtworkDownloader
                .parseHttpsUrl(source)
                ?.takeIf(AutoArtworkDownloader::isPublicHttpsUrl)
                ?: return null
        val lock = artworkLocks.getOrPut(key) { Any() }
        return synchronized(lock) {
            cachedRemoteArtwork(context, key)?.let { return@synchronized it }
            // One retry covers flaky CDNs / brief Auto host pipe races.
            // Keep the lock stripe resident so concurrent openers share the same monitor.
            fetchRemoteArtwork(sourceUrl, cacheDir, target)
                ?: fetchRemoteArtwork(sourceUrl, cacheDir, target)
        }
    }

    private fun fetchRemoteArtwork(
        url: URL,
        cacheDir: File,
        target: File,
    ): File? {
        val temp = File.createTempFile("artwork_", ".tmp", cacheDir)
        return try {
            val bytes = AutoArtworkDownloader.downloadHttpsBytes(url) ?: return null
            if (!AutoArtworkFetchLogic.looksLikeImage(bytes.copyOf(minOf(12, bytes.size)))) {
                return null
            }
            temp.outputStream().use { it.write(bytes) }
            if (temp.length() <= 0L) return null

            if (!temp.renameTo(target)) {
                val staging = File.createTempFile("artwork_stage_", ".tmp", target.parentFile)
                try {
                    temp.copyTo(staging, overwrite = true)
                    if (!staging.renameTo(target)) {
                        return null
                    }
                } finally {
                    if (staging.exists() && !staging.delete()) {
                        android.util.Log.w("CollageProvider", "Failed to delete staging file")
                    }
                }
            }
            evictArtworkCache(cacheDir, target)
            target
        } catch (error: Exception) {
            android.util.Log.w("CollageProvider", "Failed to cache artwork", error)
            null
        } finally {
            if (temp.exists() && !temp.delete()) {
                android.util.Log.w("CollageProvider", "Failed to delete temp file")
            }
        }
    }

    private fun resolveCollage(
        context: android.content.Context,
        filename: String,
    ): File? {
        if (!filename.matches(Regex("[a-zA-Z0-9_]+\\.png"))) return null
        return childFile(File(context.cacheDir, "auto_collages"), filename)
    }

    private fun resolveRegisteredLocalFile(
        context: android.content.Context,
        key: String,
    ): File? {
        if (!key.matches(HEX_64_PATTERN)) return null
        val path = AutoArtworkSourceStore.get(context, key) ?: return null
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val roots =
            listOfNotNull(
                context.cacheDir,
                context.filesDir,
                context.getExternalFilesDir(null),
                context.externalCacheDir,
            ).mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
        return candidate.takeIf { file ->
            file.isFile &&
                roots.any { root ->
                    file.path == root.path ||
                        file.path.startsWith("${root.path}${File.separator}")
                }
        }
    }

    private fun childFile(
        parent: File,
        name: String,
    ): File? {
        val canonicalParent =
            runCatching { parent.apply { mkdirs() }.canonicalFile }.getOrNull()
                ?: return null
        val child =
            runCatching { File(canonicalParent, name).canonicalFile }.getOrNull()
                ?: return null
        return child.takeIf {
            it.parentFile == canonicalParent && it.path != canonicalParent.path
        }
    }

    private fun evictArtworkCache(
        cacheDir: File,
        protectedFile: File,
    ) {
        val files =
            cacheDir
                .listFiles()
                ?.filter { it.isFile && it != protectedFile }
                ?.sortedBy(File::lastModified)
                ?.toMutableList()
                ?: return
        var totalBytes = files.sumOf(File::length) + protectedFile.length()
        while (files.size + 1 > MAX_CACHE_FILES || totalBytes > MAX_CACHE_BYTES) {
            val oldest = files.removeFirstOrNull() ?: break
            val size = oldest.length()
            if (oldest.delete()) totalBytes -= size
        }
    }

    private fun File.readHeader(maxBytes: Int): ByteArray =
        inputStream().use { input ->
            val buffer = ByteArray(maxBytes)
            val read = input.read(buffer)
            if (read <= 0) byteArrayOf() else buffer.copyOf(read)
        }

    // Unused but required
    override fun query(
        uri: Uri,
        p: Array<out String>?,
        s: String?,
        sa: Array<out String>?,
        so: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        s: String?,
        sa: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        s: String?,
        sa: Array<out String>?,
    ): Int = 0
}
