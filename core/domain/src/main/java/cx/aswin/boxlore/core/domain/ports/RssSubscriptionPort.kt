package cx.aswin.boxlore.core.domain.ports

import cx.aswin.boxlore.core.domain.RssSubscriptionResult
import cx.aswin.boxlore.core.model.Podcast

/**
 * Narrow RSS subscribe / Podcast Index link seam for Settings (and tests).
 *
 * Production: [cx.aswin.boxlore.core.rss.RssPodcastRepository].
 */
interface RssSubscriptionPort {
    suspend fun addSubscription(rawUrl: String): RssSubscriptionResult

    suspend fun confirmPodcastIndexLink(
        rssPodcastId: String,
        podcastIndexId: String,
    ): Podcast
}
