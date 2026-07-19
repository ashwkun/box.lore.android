package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearnCuriosityHistoryStoreTest {
    private lateinit var application: Application
    private lateinit var store: LearnCuriosityHistoryStore

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        store = LearnCuriosityHistoryStore(application)
        store.clearAll()
    }

    @After
    fun tearDown() {
        store.clearAll()
        store.consumePendingRestores()
    }

    private fun card(
        id: String,
        title: String = "Episode $id",
    ): LearnCuriosityCard =
        LearnCuriosityCard(
            episodeId = id,
            question = "Why $id?",
            explanation = "Because $id",
            curiosityScore = 7,
            episodeTitle = title,
            podcastTitle = "Show",
            imageUrl = "https://img/$id.jpg",
            feedImage = null,
            podcastId = "pod-$id",
            audioUrl = "https://audio/$id.mp3",
            duration = 1234,
            description = "Desc $id",
        )

    @Test
    fun emptyStoreReturnsNoEntries() {
        assertTrue(store.getEntries().isEmpty())
        assertTrue(store.getDismissedIds().isEmpty())
    }

    @Test
    fun recordDismissalPersistsEntryAndDismissedId() {
        store.recordDismissal(card("e1"), LearnHistoryAction.DISMISS)

        val entries = store.getEntries()
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("e1", entry.episodeId)
        assertEquals("Why e1?", entry.question)
        assertEquals(7, entry.curiosityScore)
        assertEquals(LearnHistoryAction.DISMISS, entry.action)
        assertTrue(entry.dismissedAtMs > 0L)
        assertTrue("e1" in store.getDismissedIds())
    }

    @Test
    fun recordDismissalMovesMostRecentToFrontWithoutDuplicating() {
        store.recordDismissal(card("e1"), LearnHistoryAction.DISMISS)
        store.recordDismissal(card("e2"), LearnHistoryAction.QUEUE)
        store.recordDismissal(card("e1", title = "Updated"), LearnHistoryAction.QUEUE)

        val entries = store.getEntries()
        assertEquals(listOf("e1", "e2"), entries.map { it.episodeId })
        assertEquals("Updated", entries.first().episodeTitle)
        assertEquals(LearnHistoryAction.QUEUE, entries.first().action)
    }

    @Test
    fun removeEntryClearsDismissedIdAndReturnsEntry() {
        store.recordDismissal(card("e1"), LearnHistoryAction.DISMISS)

        val removed = store.removeEntry("e1")

        assertEquals("e1", removed?.episodeId)
        assertTrue(store.getEntries().isEmpty())
        assertFalse("e1" in store.getDismissedIds())
    }

    @Test
    fun removeUnknownEntryReturnsNull() {
        assertNull(store.removeEntry("missing"))
    }

    @Test
    fun removeEntryWithQueueRestoreQueuesForConsumption() {
        store.recordDismissal(card("e1"), LearnHistoryAction.QUEUE)

        store.removeEntry("e1", queueRestore = true)

        val restores = store.consumePendingRestores()
        assertEquals(listOf("e1"), restores.map { it.episodeId })
        // Second consume drains the queue.
        assertTrue(store.consumePendingRestores().isEmpty())
    }

    @Test
    fun clearAllRemovesEntriesAndPendingRestores() {
        store.recordDismissal(card("e1"), LearnHistoryAction.DISMISS)
        store.removeEntry("e1", queueRestore = true)

        store.clearAll()

        assertTrue(store.getEntries().isEmpty())
        assertTrue(store.consumePendingRestores().isEmpty())
    }

    @Test
    fun malformedJsonYieldsEmptyEntries() {
        // Directly corrupt the backing store, then confirm parsing degrades gracefully.
        cx.aswin.boxlore.core.prefs
            .BoxcastPrefs(application)
            .setLearnCuriosityHistoryJson("{not-json")

        assertTrue(store.getEntries().isEmpty())
    }
}
