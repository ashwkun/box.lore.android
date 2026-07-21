package cx.aswin.boxlore.core.playback.service

import android.util.Log
import cx.aswin.boxlore.core.playback.AutoArtworkFetchLogic
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

/**
 * Shared HTTPS artwork downloader for Android Auto collage generation and
 * [AutoCollageProvider] lazy cover fetches. Validates public hosts on every hop.
 */
internal object AutoArtworkDownloader {
    private const val TAG = "AutoArtworkDl"

    fun parseHttpsUrl(raw: String): URL? {
        val normalized = raw.replaceFirst("http://", "https://")
        val url = runCatching { URI(normalized).toURL() }.getOrNull() ?: return null
        return url.takeIf { it.protocol == "https" }
    }

    fun downloadHttpsBytes(startUrl: URL): ByteArray? {
        var current = startUrl
        var redirects = 0
        while (redirects <= AutoArtworkFetchLogic.MAX_REDIRECTS) {
            when (val outcome = fetchOnce(current)) {
                null -> return null
                is FetchOutcome.Redirect -> {
                    current = outcome.url
                    redirects += 1
                }
                is FetchOutcome.Body -> return outcome.bytes
            }
        }
        return null
    }

    fun isPublicHttpsUrl(url: URL): Boolean {
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

    private fun fetchOnce(url: URL): FetchOutcome? {
        if (!isPublicHttpsUrl(url)) return null
        var connection: HttpURLConnection? = null
        return try {
            connection = openGet(url)
            val code = connection.responseCode
            if (AutoArtworkFetchLogic.isRedirect(code)) {
                val location = connection.getHeaderField("Location") ?: return null
                val next = resolveRedirect(url, location) ?: return null
                FetchOutcome.Redirect(next)
            } else if (
                !AutoArtworkFetchLogic.shouldAcceptArtwork(
                    code,
                    connection.contentType,
                    connection.contentLengthLong,
                )
            ) {
                null
            } else {
                connection.inputStream.use { input ->
                    readBounded(input)?.let(FetchOutcome::Body)
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Artwork download failed for $url", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun openGet(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = AutoArtworkFetchLogic.CONNECT_TIMEOUT_MS
        connection.readTimeout = AutoArtworkFetchLogic.READ_TIMEOUT_MS
        connection.doInput = true
        connection.connect()
        return connection
    }

    private fun resolveRedirect(
        current: URL,
        location: String,
    ): URL? =
        runCatching {
            URI(current.toURI().resolve(location).toString()).toURL()
        }.getOrNull()

    private fun readBounded(input: InputStream): ByteArray? {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val out = java.io.ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > AutoArtworkFetchLogic.MAX_ARTWORK_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun InetAddress.isUniqueLocalIpv6(): Boolean {
        val bytes = address
        return bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
    }

    private sealed class FetchOutcome {
        class Redirect(
            val url: URL,
        ) : FetchOutcome()

        class Body(
            val bytes: ByteArray,
        ) : FetchOutcome()
    }
}
