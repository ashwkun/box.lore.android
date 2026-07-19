package cx.aswin.boxlore.core.ranking

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object RankingSerialization {
    fun encode(values: DoubleArray): ByteArray {
        return ByteBuffer.allocate(values.size * Double.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach(::putDouble) }
            .array()
    }

    fun decode(bytes: ByteArray, expectedSize: Int): DoubleArray {
        require(bytes.size == expectedSize * Double.SIZE_BYTES) {
            "Expected ${expectedSize * Double.SIZE_BYTES} bytes, got ${bytes.size}"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return DoubleArray(expectedSize) { buffer.double }
    }
}
