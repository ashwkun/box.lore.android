package cx.aswin.boxlore.core.analytics

/**
 * Pure genre-persona derivation used by onboarding analytics (no PostHog dependency).
 */
object GenrePersonaLogic {
    private val KNOWLEDGE_GENRES = setOf(
        "News", "Technology", "Business", "Education", "Science", "History", "Government"
    )
    private val ENTERTAINMENT_GENRES = setOf(
        "Comedy", "True Crime", "TV & Film", "Fiction", "Music", "Arts"
    )
    private val LIFESTYLE_GENRES = setOf(
        "Health", "Sports", "Society & Culture", "Religion & Spirituality", "Kids & Family", "Leisure"
    )

    fun deriveGenrePersona(selectedGenres: Set<String>): Map<String, String> {
        val knowledgeCount = selectedGenres.count { it in KNOWLEDGE_GENRES }
        val entertainmentCount = selectedGenres.count { it in ENTERTAINMENT_GENRES }
        val lifestyleCount = selectedGenres.count { it in LIFESTYLE_GENRES }

        // 1. Breadth
        val categoriesHit = listOf(knowledgeCount, entertainmentCount, lifestyleCount).count { it > 0 }
        val breadth = when (categoriesHit) {
            1 -> "highly_focused"
            2 -> "balanced"
            3 -> "broad_explorer"
            else -> "unknown"
        }

        // 2. Enthusiasm
        val totalCount = selectedGenres.size
        val enthusiasm = when {
            totalCount <= 2 -> "casual"
            totalCount <= 5 -> "engaged"
            else -> "obsessive"
        }

        // 3. Archetypes (listener_profile)
        val profile = when {
            selectedGenres.containsAll(listOf("True Crime", "Comedy")) -> "lighthearted_detective"
            selectedGenres.containsAll(listOf("Sports", "Leisure")) -> "sports_fanatic"
            listOf("News", "Government", "History").count { it in selectedGenres } >= 2 -> "civic_junkie"
            listOf("Technology", "Business", "Science").count { it in selectedGenres } >= 2 -> "tech_professional"
            listOf("Health", "Science", "Education").count { it in selectedGenres } >= 2 -> "wellness_intellectual"
            listOf("Society & Culture", "Religion & Spirituality", "Arts").count { it in selectedGenres } >= 2 -> "cultural_philosopher"
            else -> {
                // Fallback majority bucket
                val max = maxOf(knowledgeCount, entertainmentCount, lifestyleCount)
                if (max == 0) "eclectic_explorer"
                else {
                    val tiedCount = listOf(knowledgeCount, entertainmentCount, lifestyleCount).count { it == max }
                    if (tiedCount > 1) "eclectic_explorer"
                    else when (max) {
                        knowledgeCount -> "knowledge_seeker"
                        entertainmentCount -> "entertainment_fan"
                        else -> "lifestyle_enthusiast"
                    }
                }
            }
        }

        return mapOf(
            "listener_profile" to profile,
            "genre_breadth" to breadth,
            "genre_enthusiasm" to enthusiasm
        )
    }
}
