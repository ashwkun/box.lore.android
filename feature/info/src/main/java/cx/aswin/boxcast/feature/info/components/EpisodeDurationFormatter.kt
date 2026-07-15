package cx.aswin.boxcast.feature.info.components

internal fun formatEpisodeDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
