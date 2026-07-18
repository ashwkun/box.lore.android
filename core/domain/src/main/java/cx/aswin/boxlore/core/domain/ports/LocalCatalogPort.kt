package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.model.Podcast

/**
 * Local podcast catalog seam for feature ViewModels (Home / Info) and nav helpers.
 *
 * Hides Room / [PodcastDao] so presentation never takes [BoxLoreDatabase].
 * Production: [cx.aswin.boxlore.core.data.RoomLocalCatalog].
 */
interface LocalCatalogPort {
    /** Row for [id], or null if never stored locally. */
    suspend fun getLocalPodcast(id: String): Podcast?

    /**
     * Subscribed RSS show linked to a Podcast Index id, if any.
     * Returns null when missing or not subscribed.
     */
    suspend fun getSubscribedRssLinkedTo(podcastIndexId: String): Podcast?

    /** Persist enriched metadata for an already-subscribed show. */
    suspend fun upsertSubscribedPodcast(podcast: Podcast)
}
