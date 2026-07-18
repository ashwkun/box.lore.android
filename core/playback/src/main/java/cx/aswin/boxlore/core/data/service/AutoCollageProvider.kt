package cx.aswin.boxlore.core.data.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Read-only artwork bridge for the Android Auto host process.
 *
 * Folder collages are pre-generated. Remote cover URLs are registered by
 * [cx.aswin.boxlore.core.data.service.auto.AutoArtworkRepository] and fetched
 * lazily into a bounded app-cache location when the host opens the URI.
 */
class AutoCollageProvider : ContentProvider() {
    companion object {
        private const val SOURCE_PREFS = "android_auto_artwork_sources"
        private const val MAX_ARTWORK_BYTES = 8L * 1024L * 1024L
        private const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
        private const val MAX_CACHE_FILES = 200
        private val artworkLocks = ConcurrentHashMap<String, Any>()
        private val HEX_64_PATTERN = Regex("[a-f0-9]{64}")

        fun getUri(
            context: android.content.Context,
            filename: String,
        ): Uri =
            Uri
                .Builder()
                .scheme("content")
                .authority("${context.packageName}.collage")
                .appendPath("collage")
                .appendPath(filename)
                .build()
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
            ?.takeIf { it.isFile && it.length() in 1..MAX_ARTWORK_BYTES }
    }

    private fun getOrFetchRemoteArtwork(
        context: android.content.Context,
        key: String,
    ): File? {
        if (!key.matches(HEX_64_PATTERN)) return null
        val cacheDir = File(context.cacheDir, "auto_artwork").apply { mkdirs() }
        val target = childFile(cacheDir, key) ?: return null
        if (target.isFile && target.length() in 1..MAX_ARTWORK_BYTES) return target

        val source =
            context
                .getSharedPreferences(SOURCE_PREFS, android.content.Context.MODE_PRIVATE)
                .getString(key, null)
                ?: return null
        val sourceUrl =
            runCatching { java.net.URI(source).toURL() }
                .getOrNull()
                ?.takeIf(::isPublicHttpsUrl)
                ?: return null
        return synchronized(artworkLocks.getOrPut(key) { Any() }) {
            if (target.isFile && target.length() in 1..MAX_ARTWORK_BYTES) {
                return@synchronized target
            }
            fetchRemoteArtwork(sourceUrl, cacheDir, target).also {
                artworkLocks.remove(key)
            }
        }
    }

    private fun verifyConnection(connection: HttpURLConnection): Boolean {
        val contentLength = connection.contentLengthLong
        val contentType = connection.contentType.orEmpty().lowercase()
        return connection.responseCode in 200..299 &&
            contentType.startsWith("image/") &&
            contentLength <= MAX_ARTWORK_BYTES
    }

    private fun downloadStream(
        connection: HttpURLConnection,
        temp: File,
    ): Boolean {
        return try {
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_ARTWORK_BYTES) return false
                        output.write(buffer, 0, read)
                    }
                }
            }
            temp.length() > 0L
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchRemoteArtwork(
        url: URL,
        cacheDir: File,
        target: File,
    ): File? {
        val temp = File.createTempFile("artwork_", ".tmp", cacheDir)
        var connection: HttpURLConnection? = null
        return try {
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.doInput = true
            connection.connect()

            if (!verifyConnection(connection) || !downloadStream(connection, temp)) {
                return null
            }

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
            connection?.disconnect()
            if (temp.exists() && !temp.delete()) {
                android.util.Log.w("CollageProvider", "Failed to delete temp file")
            }
        }
    }

    private fun isPublicHttpsUrl(url: URL): Boolean {
        if (url.protocol != "https" || (url.port != -1 && url.port != 443)) return false
        val addresses =
            runCatching { InetAddress.getAllByName(url.host) }.getOrNull()
                ?: return false
        return addresses.isNotEmpty() &&
            addresses.all { address ->
                !address.isAnyLocalAddress &&
                    !address.isLoopbackAddress &&
                    !address.isLinkLocalAddress &&
                    !address.isSiteLocalAddress &&
                    !address.isMulticastAddress &&
                    !address.isUniqueLocalIpv6()
            }
    }

    private fun InetAddress.isUniqueLocalIpv6(): Boolean {
        val bytes = address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
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
        val path =
            context
                .getSharedPreferences(SOURCE_PREFS, android.content.Context.MODE_PRIVATE)
                .getString(key, null)
                ?: return null
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
