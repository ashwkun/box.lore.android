package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoArtworkRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ---- remoteUri ----

    @Test
    fun remoteUriReturnsNullForNullOrBlank() {
        assertNull(AutoArtworkRepository.remoteUri(context, null))
        assertNull(AutoArtworkRepository.remoteUri(context, "   "))
    }

    @Test
    fun remoteUriBuildsContentUriForHttpsSource() {
        val uri = AutoArtworkRepository.remoteUri(context, "https://cdn.example/art.jpg")!!

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.collage", uri.authority)
        assertEquals(listOf("art"), uri.pathSegments.take(1))
        assertEquals(2, uri.pathSegments.size)
    }

    @Test
    fun remoteUriUpgradesHttpToHttpsAndStoresMapping() {
        val httpsUri = AutoArtworkRepository.remoteUri(context, "https://cdn.example/x.jpg")!!
        val httpUri = AutoArtworkRepository.remoteUri(context, "http://cdn.example/x.jpg")!!

        // http:// normalizes to https://, producing the same sha256 key/path.
        assertEquals(httpsUri.pathSegments.last(), httpUri.pathSegments.last())
    }

    @Test
    fun remoteUriRejectsNonHttpSchemes() {
        assertNull(AutoArtworkRepository.remoteUri(context, "ftp://cdn.example/x.jpg"))
    }

    @Test
    fun remoteUriMapsLocalFileInsideAllowedRoot() {
        val file = File(context.cacheDir, "art.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val uri = AutoArtworkRepository.remoteUri(context, file.absolutePath)!!

        assertEquals("content", uri.scheme)
        assertEquals(listOf("local"), uri.pathSegments.take(1))
    }

    @Test
    fun remoteUriHandlesFileScheme() {
        val file = File(context.filesDir, "scheme.png").apply { writeBytes(byteArrayOf(9)) }

        val uri = AutoArtworkRepository.remoteUri(context, "file://${file.absolutePath}")!!

        assertEquals("local", uri.pathSegments.first())
    }

    @Test
    fun remoteUriRejectsLocalFileOutsideAllowedRoots() {
        val outside = File.createTempFile("outside", ".png").apply { deleteOnExit() }

        assertNull(AutoArtworkRepository.remoteUri(context, outside.absolutePath))
    }

    @Test
    fun remoteUriRejectsMissingLocalFile() {
        val missing = File(context.cacheDir, "does-not-exist.png")

        assertNull(AutoArtworkRepository.remoteUri(context, missing.absolutePath))
    }

    // ---- collageUri ----

    @Test
    fun collageUriReturnsNullWhenCollageMissing() {
        assertNull(AutoArtworkRepository.collageUri(context, "folder-1"))
    }

    @Test
    fun collageUriReturnsUriWhenCollageExists() {
        val dir = File(context.cacheDir, "auto_collages").apply { mkdirs() }
        File(dir, "folder_1.png").writeBytes(byteArrayOf(1))

        val uri = AutoArtworkRepository.collageUri(context, "folder-1")!!

        assertEquals("content", uri.scheme)
        assertEquals("collage", uri.pathSegments.first())
        assertEquals("folder_1.png", uri.pathSegments.last())
    }

    @Test
    fun collageUriSanitizesFolderIdIntoFilename() {
        val dir = File(context.cacheDir, "auto_collages").apply { mkdirs() }
        // "a/b c" → "a_b_c" per safeFileName().
        File(dir, "a_b_c.png").writeBytes(byteArrayOf(1))

        val uri = AutoArtworkRepository.collageUri(context, "a/b c")!!
        assertEquals("a_b_c.png", uri.pathSegments.last())
    }
}
