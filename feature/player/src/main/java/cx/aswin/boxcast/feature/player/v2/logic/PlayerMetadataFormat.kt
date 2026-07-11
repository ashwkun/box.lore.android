package cx.aswin.boxcast.feature.player.v2.logic

/** Formats metadata labels shown on player chips (unit-testable with fixed clock). */
object PlayerMetadataFormat {
    fun formatRelativeDate(
        timestampSeconds: Long,
        nowSeconds: Long,
    ): String {
        if (timestampSeconds == 0L) return ""
        val diff = nowSeconds - timestampSeconds
        return when {
            diff < 0 -> "just now"
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            diff < 604800 -> "${diff / 86400}d ago"
            diff < 2_592_000 -> "${diff / 604800}w ago"
            diff < 31_536_000 -> "${diff / 2_592_000}mo ago"
            else -> "${diff / 31_536_000}y ago"
        }
    }

    fun formatDurationLabel(seconds: Int): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
