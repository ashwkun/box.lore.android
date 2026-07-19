package cx.aswin.boxlore.core.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Exhaustive branch coverage for [GenrePersonaLogic.deriveGenrePersona] — every archetype,
 * breadth tier, and enthusiasm bucket.
 */
class GenrePersonaLogicTest {
    private fun persona(vararg genres: String) = GenrePersonaLogic.deriveGenrePersona(genres.toSet())

    // ---- breadth ----

    @Test
    fun singleCategoryIsHighlyFocused() {
        assertEquals("highly_focused", persona("News")["genre_breadth"])
    }

    @Test
    fun twoCategoriesIsBalanced() {
        assertEquals("balanced", persona("News", "Comedy")["genre_breadth"])
    }

    @Test
    fun threeCategoriesIsBroadExplorer() {
        assertEquals("broad_explorer", persona("News", "Comedy", "Health")["genre_breadth"])
    }

    @Test
    fun emptySelectionIsUnknownBreadth() {
        assertEquals("unknown", persona()["genre_breadth"])
    }

    // ---- enthusiasm ----

    @Test
    fun twoOrFewerGenresIsCasual() {
        assertEquals("casual", persona("News")["genre_enthusiasm"])
        assertEquals("casual", persona("News", "Comedy")["genre_enthusiasm"])
    }

    @Test
    fun threeToFiveGenresIsEngaged() {
        assertEquals("engaged", persona("News", "Comedy", "Health")["genre_enthusiasm"])
        assertEquals(
            "engaged",
            persona("News", "Comedy", "Health", "Sports", "Music")["genre_enthusiasm"],
        )
    }

    @Test
    fun sixOrMoreGenresIsObsessive() {
        assertEquals(
            "obsessive",
            persona("News", "Technology", "Business", "Education", "Science", "History")["genre_enthusiasm"],
        )
    }

    // ---- listener_profile archetypes (checked in declaration order) ----

    @Test
    fun trueCrimePlusComedyIsLightheartedDetective() {
        assertEquals("lighthearted_detective", persona("True Crime", "Comedy")["listener_profile"])
    }

    @Test
    fun sportsPlusLeisureIsSportsFanatic() {
        assertEquals("sports_fanatic", persona("Sports", "Leisure")["listener_profile"])
    }

    @Test
    fun twoCivicGenresIsCivicJunkie() {
        assertEquals("civic_junkie", persona("News", "Government")["listener_profile"])
    }

    @Test
    fun twoTechGenresIsTechProfessional() {
        assertEquals("tech_professional", persona("Technology", "Business")["listener_profile"])
    }

    @Test
    fun healthAndEducationIsWellnessIntellectual() {
        assertEquals("wellness_intellectual", persona("Health", "Education")["listener_profile"])
    }

    @Test
    fun cultureGenresAreCulturalPhilosopher() {
        assertEquals("cultural_philosopher", persona("Society & Culture", "Arts")["listener_profile"])
    }

    // ---- listener_profile fallback majority buckets ----

    @Test
    fun singleKnowledgeGenreIsKnowledgeSeeker() {
        assertEquals("knowledge_seeker", persona("News")["listener_profile"])
    }

    @Test
    fun singleEntertainmentGenreIsEntertainmentFan() {
        assertEquals("entertainment_fan", persona("Comedy")["listener_profile"])
    }

    @Test
    fun singleLifestyleGenreIsLifestyleEnthusiast() {
        assertEquals("lifestyle_enthusiast", persona("Health")["listener_profile"])
    }

    @Test
    fun tiedMajorityIsEclecticExplorer() {
        assertEquals("eclectic_explorer", persona("News", "Comedy")["listener_profile"])
    }

    @Test
    fun emptySelectionIsEclecticExplorer() {
        assertEquals("eclectic_explorer", persona()["listener_profile"])
    }
}
