package android.util

/** JVM unit-test stub so production [Log] calls compile and no-op without Robolectric. */
object Log {
    @JvmStatic fun v(
        tag: String,
        msg: String,
    ): Int = 0

    @JvmStatic fun d(
        tag: String,
        msg: String,
    ): Int = 0

    @JvmStatic fun i(
        tag: String,
        msg: String,
    ): Int = 0

    @JvmStatic fun w(
        tag: String,
        msg: String,
    ): Int = 0

    @JvmStatic fun w(
        tag: String,
        msg: String,
        tr: Throwable,
    ): Int = 0

    @JvmStatic fun e(
        tag: String,
        msg: String,
    ): Int = 0

    @JvmStatic fun e(
        tag: String,
        msg: String,
        tr: Throwable,
    ): Int = 0

    @JvmStatic fun isLoggable(
        tag: String,
        level: Int,
    ): Boolean = false
}
