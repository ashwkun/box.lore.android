package cx.aswin.boxlore.core.catalog.home

import java.util.Locale

/**
 * Maps a listener region to language tags for Home / recommendation retrieval.
 * Avoids hardcoding English-only requests for non-English chart countries.
 */
fun recommendationLanguagesForCountry(country: String?): List<String> {
    val normalized = country?.lowercase(Locale.ROOT)?.takeIf { it.length in 2..3 } ?: "us"
    return when (normalized) {
        "fr" -> listOf("fr", "en")
        "in" -> listOf("en", "hi")
        "de" -> listOf("de", "en")
        "es", "mx", "ar" -> listOf("es", "en")
        "br", "pt" -> listOf("pt", "en")
        "jp" -> listOf("ja", "en")
        else -> listOf("en")
    }
}
