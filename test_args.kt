import android.net.Uri

fun encode(s: String?) = Uri.encode(s?.ifEmpty { "_" } ?: "_")
