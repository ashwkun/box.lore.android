package cx.aswin.boxlore.core.playback.service

import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CoilBitmapLoader(
    private val context: android.content.Context,
    private val serviceScope: CoroutineScope,
) : androidx.media3.common.util.BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> =
        try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) {
                com.google.common.util.concurrent.Futures
                    .immediateFuture(bitmap)
            } else {
                com.google.common.util.concurrent.Futures.immediateFailedFuture(
                    IllegalArgumentException("Could not decode bitmap"),
                )
            }
        } catch (e: Exception) {
            com.google.common.util.concurrent.Futures
                .immediateFailedFuture(e)
        }

    override fun loadBitmap(uri: android.net.Uri): com.google.common.util.concurrent.ListenableFuture<android.graphics.Bitmap> =
        serviceScope.future {
            try {
                android.util.Log.d("BoxCastPlayer", "CoilBitmapLoader: loadBitmap started for $uri")
                val loader = coil.Coil.imageLoader(context)
                val request =
                    coil.request.ImageRequest
                        .Builder(context)
                        .data(uri)
                        .allowHardware(false) // Required: system notifications cannot use hardware-backed bitmaps
                        .build()
                val result = loader.execute(request)
                val bitmap = (result as? coil.request.SuccessResult)?.drawable?.toBitmap()
                if (bitmap != null) {
                    android.util.Log.d("BoxCastPlayer", "CoilBitmapLoader: loadBitmap succeeded for $uri")
                    bitmap
                } else {
                    val errorMsg =
                        "CoilBitmapLoader: result is not a success or drawable could not be converted to bitmap for $uri"
                    android.util.Log.e("BoxCastPlayer", errorMsg)
                    throw IllegalArgumentException(errorMsg)
                }
            } catch (e: Exception) {
                android.util.Log.e("BoxCastPlayer", "CoilBitmapLoader: loadBitmap failed for $uri", e)
                throw e
            }
        }
}
