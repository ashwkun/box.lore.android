package cx.aswin.boxlore.core.downloads

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

object DownloadSpeedLimiter {
    @Volatile
    var speedLimitBps: Long = 0L // 0 means unlimited
}

class ThrottlingDataSource(
    private val delegate: DataSource
) : DataSource {
    private var bytesReadThisSecond = 0L
    private var secondStartMs = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        secondStartMs = System.currentTimeMillis()
        bytesReadThisSecond = 0L
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val limit = DownloadSpeedLimiter.speedLimitBps
        if (limit <= 0) {
            return delegate.read(buffer, offset, length)
        }

        val now = System.currentTimeMillis()
        if (now - secondStartMs >= 1000) {
            secondStartMs = now
            bytesReadThisSecond = 0L
        }

        if (bytesReadThisSecond >= limit) {
            val sleepMs = 1000 - (now - secondStartMs)
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs)
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
            secondStartMs = System.currentTimeMillis()
            bytesReadThisSecond = 0L
        }

        // Limit the read size to avoid exceeding speedLimit
        val maxToRead = (limit - bytesReadThisSecond).coerceAtMost(length.toLong()).coerceAtLeast(1).toInt()
        val readBytes = delegate.read(buffer, offset, maxToRead)
        if (readBytes > 0) {
            bytesReadThisSecond += readBytes
        }
        return readBytes
    }

    override fun getUri(): Uri? = delegate.getUri()

    override fun close() {
        delegate.close()
    }
}
