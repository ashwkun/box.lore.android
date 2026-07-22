package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.HomeEditorialIcon
import cx.aswin.boxlore.feature.home.HomeEditorialRow
import cx.aswin.boxlore.feature.home.SmartHeroItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeDiscoveryLogicTest {
    @Test
    fun `discover podcasts excluding returns null when trending is empty`() {
        assertNull(
            discoverPodcastsExcluding(
                trending = emptyList(),
                heroItems = emptyList(),
                editorialRows = emptyList(),
            ),
        )
    }

    @Test
    fun `discover podcasts excluding filters hero and editorial row podcasts`() {
        val trending =
            listOf(
                TestFixtures.podcast(id = "hero", title = "Hero"),
                TestFixtures.podcast(id = "editorial", title = "Editorial"),
                TestFixtures.podcast(id = "free", title = "Free"),
            )
        val heroItems =
            listOf(
                SmartHeroItem(
                    type = HeroType.SPOTLIGHT,
                    podcast = trending[0],
                    label = "Hero",
                ),
            )

        val result =
            discoverPodcastsExcluding(
                trending = trending,
                heroItems = heroItems,
                editorialRows = listOf(editorialRow(trending[1])),
            )

        assertEquals(listOf("free"), result?.map { it.id })
    }

    @Test
    fun `discover podcasts excluding returns empty list when all trending shows are filtered`() {
        val trending =
            listOf(
                TestFixtures.podcast(id = "hero", title = "Hero"),
                TestFixtures.podcast(id = "editorial", title = "Editorial"),
            )
        val heroItems =
            listOf(
                SmartHeroItem(
                    type = HeroType.SPOTLIGHT,
                    podcast = trending[0],
                    label = "Hero",
                ),
            )

        val result =
            discoverPodcastsExcluding(
                trending = trending,
                heroItems = heroItems,
                editorialRows = listOf(editorialRow(trending[1])),
            )

        assertEquals(emptyList<String>(), result?.map { it.id })
    }

    private fun editorialRow(podcast: Podcast): HomeEditorialRow =
        HomeEditorialRow(
            providerId = "science_explainer",
            title = "Worth knowing",
            subtitle = "Clear answers",
            icon = HomeEditorialIcon.SCIENCE,
            podcasts = listOf(podcast),
        )
}
