package cx.aswin.boxcast.core.data.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * A publicly readable ContentProvider that serves collage images from cache
 * to Android Auto. Unlike FileProvider, this is exported and allows any
 * reader (including the Android Auto projection process) to access the files.
 *
 * URI format: content://cx.aswin.boxcast.collage/{filename}
 * Example:    content://cx.aswin.boxcast.collage/home_resume.png
 */
class AutoCollageProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "cx.aswin.boxcast.collage"
        
        fun getUri(filename: String): Uri {
            return Uri.parse("content://$AUTHORITY/$filename")
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val filename = uri.lastPathSegment ?: return null
        val context = context ?: return null
        val file = File(File(context.cacheDir, "auto_collages"), filename)
        
        if (!file.exists()) {
            android.util.Log.w("CollageProvider", "File not found: ${file.absolutePath}")
            return null
        }
        
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    // Unused but required
    override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<out String>?): Int = 0
}
