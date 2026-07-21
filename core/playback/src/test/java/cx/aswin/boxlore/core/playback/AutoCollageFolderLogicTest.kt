package cx.aswin.boxlore.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCollageFolderLogicTest {
    @Test
    fun pairOrNullRequiresBothKeyAndImage() {
        assertNull(AutoCollageFolderLogic.pairOrNull(null, "https://cdn/a.jpg"))
        assertNull(AutoCollageFolderLogic.pairOrNull("ep-1", null))
        assertNull(AutoCollageFolderLogic.pairOrNull("  ", "https://cdn/a.jpg"))
        assertNull(AutoCollageFolderLogic.pairOrNull("ep-1", "  "))
        val pair = AutoCollageFolderLogic.pairOrNull("ep-1", "https://cdn/a.jpg")!!
        assertEquals("ep-1", pair.contentKey)
        assertEquals("https://cdn/a.jpg", pair.imageUrl)
    }

    @Test
    fun takeAlignedKeepsKeysAndImagesInLockstep() {
        val pairs =
            listOf(
                AutoCollageFolderLogic.ArtPair("a", "https://cdn/a.jpg"),
                AutoCollageFolderLogic.ArtPair("", "https://cdn/drop.jpg"),
                AutoCollageFolderLogic.ArtPair("b", ""),
                AutoCollageFolderLogic.ArtPair("c", "https://cdn/c.jpg"),
                AutoCollageFolderLogic.ArtPair("d", "https://cdn/d.jpg"),
                AutoCollageFolderLogic.ArtPair("e", "https://cdn/e.jpg"),
            )
        val taken = AutoCollageFolderLogic.takeAligned(pairs, limit = 3)
        assertEquals(listOf("a", "c", "d"), AutoCollageFolderLogic.keysOf(taken))
        assertEquals(
            listOf("https://cdn/a.jpg", "https://cdn/c.jpg", "https://cdn/d.jpg"),
            AutoCollageFolderLogic.imagesOf(taken),
        )
    }

    @Test
    fun homeStyleConcatUsesSameFilterThenTake() {
        val history =
            listOf(
                AutoCollageFolderLogic.ArtPair("h1", "https://cdn/h1.jpg"),
                AutoCollageFolderLogic.ArtPair("h2", ""), // dropped
            )
        val newest =
            listOf(
                AutoCollageFolderLogic.ArtPair("n1", "https://cdn/n1.jpg"),
                AutoCollageFolderLogic.ArtPair("n2", "https://cdn/n2.jpg"),
            )
        val home = AutoCollageFolderLogic.takeAligned(history + newest, limit = 3)
        assertEquals(listOf("h1", "n1", "n2"), AutoCollageFolderLogic.keysOf(home))
        assertEquals(
            listOf("https://cdn/h1.jpg", "https://cdn/n1.jpg", "https://cdn/n2.jpg"),
            AutoCollageFolderLogic.imagesOf(home),
        )
    }
}
