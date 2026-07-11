package cx.aswin.boxcast.core.data

import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.network.model.EpisodeItem
import cx.aswin.boxcast.core.network.model.HistoryItem

/**
 * Represents an episode in the queue along with its podcast context.
 * This is needed because fallback episodes may come from different podcasts.
 */
data class QueueEntry(
    val episode: EpisodeItem,
    val podcast: Podcast,
    val source: String
)

interface SmartQueueEngine {

    companion object {
        const val SOURCE_SAME_PODCAST = "same_podcast"
        const val SOURCE_RESUME = "resume"
        const val SOURCE_SUBSCRIPTION = "subscription"
        /** Legacy persisted rows; new refills use [SOURCE_PERSONALIZED_REC] or [SOURCE_SIMILAR_EPISODE]. */
        const val SOURCE_SERVER_REC = "server_rec"
        const val SOURCE_PERSONALIZED_REC = "personalized_rec"
        const val SOURCE_SIMILAR_EPISODE = "similar_episode"
        /** Tier 3.5: similar to a recently liked episode (distinct label from [SOURCE_SIMILAR_EPISODE]). */
        const val SOURCE_SIMILAR_LIKED = "similar_liked"
        const val SOURCE_TRENDING = "trending"

        /**
         * AUTO_FILL sources where the user got a single cross-show suggestion, not an
         * intentional binge. Refills anchored on these skip Tier 0 deep continuation.
         */
        val DISCOVERY_REFILL_SOURCES: Set<String> = setOf(
            SOURCE_RESUME,
            SOURCE_SUBSCRIPTION,
            SOURCE_SERVER_REC,
            SOURCE_PERSONALIZED_REC,
            SOURCE_SIMILAR_EPISODE,
            SOURCE_SIMILAR_LIKED,
            SOURCE_TRENDING
        )

        /** True when Tier 0 may queue a deep run of same-show episodes after [contextSourceId]. */
        fun allowsSamePodcastContinuation(contextSourceId: String?): Boolean =
            contextSourceId == null ||
                contextSourceId == SOURCE_SAME_PODCAST ||
                contextSourceId !in DISCOVERY_REFILL_SOURCES
    }

    /**
     * Builds a batch of queue entries to append after [currentEpisode].
     *
     * @param excludeEpisodeIds episode IDs already in the live player queue — the engine
     *   never suggests these (on top of completed episodes and skip-memory rejections).
     * @param currentContextSourceId provenance of the episode currently playing (from the
     *   queue row's contextSourceId). When the user landed via a discovery refill
     *   (resume, rec, similar, trending, etc.), Tier 0 same-show continuation is skipped
     *   so the queue does not backfill an entire archive after one suggested episode.
     */
    suspend fun getNextEpisodes(
        currentEpisode: EpisodeItem,
        podcast: Podcast?,
        preferredSort: String? = null,
        excludeEpisodeIds: Set<String> = emptySet(),
        currentContextSourceId: String? = null
    ): List<QueueEntry>
}

/**
 * Tiered, offline-first queue refill engine.
 *
 * Tier 0 — same-podcast continuation (respects preferredSort + serial/episodic type,
 *          skips completed episodes and trailers). Skipped when the currently playing
 *          episode arrived via a discovery refill (resume/rec/similar/trending/etc.).
 * Tier 1 — resume one in-progress episode (10-90% progress, played in the last 30 days).
 * Tier 2 — subscriptions ranked by [PodcastScoring] (newest unplayed, round-robin).
 * Tier 3 — signal-aware network pick, only when local tiers are thin:
 *          warm users (meaningful listen history) → /recommendations;
 *          cold users with episode metadata → /episodes/similar on the current episode;
 *          otherwise skip straight to Tier 4. Failures degrade silently.
 * Tier 3.5 — one episode similar to the most recent liked episode, only when Tier 3
 *          did not already use episode-similar on the current show.
 * Tier 4 — trending in the user's region + current genre, as a last resort.
 *
 * All tiers filter against the live queue, completed episodes, and [QueueSkipMemory];
 * podcasts with repeated skips are down-ranked in the fallback tiers.
 */
class DefaultSmartQueueEngine(
    private val sources: SmartQueueSources,
    private val skipMemory: QueueSkipMemory? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) : SmartQueueEngine {

    companion object {
        /** Deep continuation cap when bingeing within a show. */
        const val SAME_PODCAST_BATCH_LIMIT = 20

        /** Mixed fallback batches aim for this many items... */
        const val FALLBACK_BATCH_TARGET = 5

        /** ...and network tiers only fire when local tiers produced fewer than this. */
        const val MIN_ITEMS_BEFORE_NETWORK = 3

        /** A resume nudge, not a rerun: at most one resume item per batch. */
        const val MAX_RESUME_PER_BATCH = 1

        const val RESUME_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
        const val RECENT_PODCAST_WINDOW_MS = 12L * 60 * 60 * 1000
        const val SHORT_EPISODE_MAX_SECONDS = 20 * 60

        /** Bound network cost: max feed fetches while hunting for fallback episodes. */
        const val MAX_SUBSCRIPTION_FEED_FETCHES = 6
        const val MAX_TRENDING_FEED_FETCHES = 5
    }

    override suspend fun getNextEpisodes(
        currentEpisode: EpisodeItem,
        podcast: Podcast?,
        preferredSort: String?,
        excludeEpisodeIds: Set<String>,
        currentContextSourceId: String?
    ): List<QueueEntry> {
        android.util.Log.d("SmartQueue", "getNextEpisodes: epId=${currentEpisode.id}, podcast=${podcast?.title}, sort=$preferredSort, contextSource=$currentContextSourceId, exclude=${excludeEpisodeIds.size}")
        if (podcast == null) return emptyList()

        skipMemory?.prune()
        val skippedEpisodes = skipMemory?.skippedEpisodeIds() ?: emptySet()
        val downRankedPodcasts = skipMemory?.downRankedPodcastIds() ?: emptySet()
        val completed = runCatching { sources.getCompletedEpisodeIds() }.getOrDefault(emptySet())

        val currentId = currentEpisode.id.toString()
        val exclude = HashSet<String>(excludeEpisodeIds.size + completed.size + skippedEpisodes.size + 1).apply {
            addAll(excludeEpisodeIds)
            addAll(completed)
            addAll(skippedEpisodes)
            add(currentId)
        }

        // Daily briefings are standalone (no feed in the podcast index): skip straight to
        // the fallback tiers using the News genre, preferring short follow-ups.
        val isBriefing = podcast.id.startsWith("briefing_")
        val effectivePodcast = if (isBriefing) podcast.copy(genre = "News") else podcast

        val batch = mutableListOf<QueueEntry>()

        // ── Tier 0: same-podcast continuation ─────────────────────────────
        if (!isBriefing && SmartQueueEngine.allowsSamePodcastContinuation(currentContextSourceId)) {
            val sameShow = sameShowContinuation(currentEpisode, effectivePodcast, preferredSort, exclude)
            batch += sameShow
            sameShow.forEach { exclude.add(it.episode.id.toString()) }
            if (batch.size >= MIN_ITEMS_BEFORE_NETWORK) {
                // Binge fast path: plenty of the same show left, no fallback needed.
                android.util.Log.d("SmartQueue", "Tier 0 satisfied batch with ${batch.size} same-show episodes")
                return batch
            }
        } else if (!isBriefing) {
            android.util.Log.d("SmartQueue", "Tier 0 skipped — discovery landing (source=$currentContextSourceId)")
        }

        // ── Fallback assembly (Tiers 1-4) ──────────────────────────────────
        val recentPodcasts = runCatching {
            sources.getRecentlyPlayedPodcastIds(nowMs() - RECENT_PODCAST_WINDOW_MS)
        }.getOrDefault(emptySet())

        val fallback = mutableListOf<QueueEntry>()

        // Tier 1: resume nudge
        fallback += resumeCandidates(effectivePodcast, exclude)

        // Tier 2: scored subscriptions
        if (batch.size + fallback.size < FALLBACK_BATCH_TARGET) {
            fallback += scoredSubscriptionEpisodes(
                currentPodcast = effectivePodcast,
                exclude = exclude,
                recentPodcasts = recentPodcasts,
                downRankedPodcasts = downRankedPodcasts,
                needed = FALLBACK_BATCH_TARGET - batch.size - fallback.size
            )
        }

        // Tiers 3/3.5/4: network, only when local tiers came up thin
        if (batch.size + fallback.size < MIN_ITEMS_BEFORE_NETWORK) {
            val region = sources.getRegion()

            val tier3 = networkTier3(
                currentEpisode = currentEpisode,
                currentPodcast = effectivePodcast,
                exclude = exclude,
                downRankedPodcasts = downRankedPodcasts,
                region = region,
                needed = FALLBACK_BATCH_TARGET - batch.size - fallback.size
            )
            fallback += tier3.entries

            if (batch.size + fallback.size < FALLBACK_BATCH_TARGET && !tier3.usedEpisodeSimilar) {
                fallback += likedSimilarBoost(exclude, region)
            }

            if (batch.size + fallback.size < MIN_ITEMS_BEFORE_NETWORK) {
                fallback += trendingEpisodes(
                    currentPodcast = effectivePodcast,
                    exclude = exclude,
                    recentPodcasts = recentPodcasts,
                    downRankedPodcasts = downRankedPodcasts,
                    region = region,
                    needed = FALLBACK_BATCH_TARGET - batch.size - fallback.size
                )
            }
        }

        // Briefing follow-ups: soft preference for short episodes (stable sort keeps
        // relative tier ordering within each duration bucket).
        val orderedFallback = if (isBriefing) {
            fallback.sortedBy { entry ->
                val duration = entry.episode.duration ?: 0
                if (duration in 1..SHORT_EPISODE_MAX_SECONDS) 0 else 1
            }
        } else {
            fallback
        }

        batch += orderedFallback
        android.util.Log.d("SmartQueue", "Returning batch of ${batch.size}: ${batch.groupingBy { it.source }.eachCount()}")
        return batch.distinctBy { it.episode.id }
    }

    // ── Tier 0 ─────────────────────────────────────────────────────────────

    private suspend fun sameShowContinuation(
        currentEpisode: EpisodeItem,
        podcast: Podcast,
        preferredSort: String?,
        exclude: Set<String>
    ): List<QueueEntry> {
        val allEpisodes = runCatching { sources.getEpisodes(podcast.id) }.getOrDefault(emptyList())
        if (allEpisodes.isEmpty()) return emptyList()

        val currentId = currentEpisode.id.toString()
        // Match subscription defaults: serial → oldest, episodic → newest.
        val sort = preferredSort ?: podcast.preferredSort
            ?: if (podcast.type == "serial") "oldest" else "newest"
        val isSerialListening = podcast.type == "serial" || sort == "oldest"
        // Episodic / news listening jumps forward to fresher episodes only — never
        // rewinds into the archive when the user is already on (or past) the latest.
        val newestFirst = !isSerialListening &&
            (sort == "newest" || podcast.genre.equals("News", ignoreCase = true))

        val currentPublished = allEpisodes.firstOrNull { it.id == currentId }?.publishedDate
            ?: currentEpisode.datePublished
            ?: 0L

        val candidates: List<Episode> = if (newestFirst) {
            allEpisodes
                .filter { it.publishedDate > currentPublished }
                .sortedByDescending { it.publishedDate }
        } else {
            val chronological = allEpisodes.sortedBy { it.publishedDate }
            val idx = chronological.indexOfFirst { it.id == currentId }
            if (idx == -1) emptyList() else chronological.drop(idx + 1)
        }

        android.util.Log.d(
            "SmartQueue",
            "Tier0 ${podcast.title}: type=${podcast.type}, sort=$sort, " +
                "mode=${if (newestFirst) "newest-forward" else "serial-chrono"}, " +
                "currentId=$currentId, currentPub=$currentPublished, " +
                "feedSize=${allEpisodes.size}, rawCandidates=${candidates.size}"
        )

        val picks = candidates.asSequence()
            .filter { it.id != currentId && it.id !in exclude }
            .filter { it.episodeType != "trailer" }
            .filter { it.audioUrl.isNotBlank() }
            .take(SAME_PODCAST_BATCH_LIMIT)
            .map { it.toQueueEntry(podcast, SmartQueueEngine.SOURCE_SAME_PODCAST) }
            .toList()

        if (picks.isNotEmpty()) {
            android.util.Log.d(
                "SmartQueue",
                "Tier0 picks: ${picks.take(5).joinToString { "${it.episode.id}(pub=${allEpisodes.firstOrNull { e -> e.id == it.episode.id.toString() }?.publishedDate})" }}" +
                    if (picks.size > 5) " +${picks.size - 5} more" else ""
            )
        } else {
            android.util.Log.d("SmartQueue", "Tier0 empty — ${if (newestFirst) "no newer episodes after current" else "show exhausted or all filtered"}")
        }

        return picks
    }

    // ── Tier 1 ─────────────────────────────────────────────────────────────

    private suspend fun resumeCandidates(
        currentPodcast: Podcast,
        exclude: MutableSet<String>
    ): List<QueueEntry> {
        val cutoff = nowMs() - RESUME_WINDOW_MS
        val picks = runCatching { sources.getResumeCandidates() }.getOrDefault(emptyList())
            .asSequence()
            .filter { it.episodeId !in exclude }
            .filter { it.podcastId != currentPodcast.id }
            .filter { it.lastPlayedAt >= cutoff }
            .filter { !it.episodeAudioUrl.isNullOrBlank() }
            .filter {
                val ratio = if (it.durationMs > 0) it.progressMs.toDouble() / it.durationMs else 0.0
                ratio in 0.10..0.90
            }
            .take(MAX_RESUME_PER_BATCH)
            .map { entity ->
                val episodeItem = EpisodeItem(
                    id = entity.episodeId.toLongOrNull() ?: 0L,
                    title = entity.episodeTitle,
                    description = entity.episodeDescription,
                    enclosureUrl = entity.episodeAudioUrl,
                    duration = (entity.durationMs / 1000L).toInt(),
                    image = entity.episodeImageUrl,
                    feedImage = entity.podcastImageUrl,
                    enclosureType = entity.enclosureType
                )
                val pod = Podcast(
                    id = entity.podcastId,
                    title = entity.podcastName,
                    artist = "",
                    imageUrl = entity.podcastImageUrl ?: entity.episodeImageUrl ?: ""
                )
                QueueEntry(episodeItem, pod, SmartQueueEngine.SOURCE_RESUME)
            }
            .filter { it.episode.id != 0L }
            .toList()
        picks.forEach { exclude.add(it.episode.id.toString()) }
        return picks
    }

    // ── Tier 2 ─────────────────────────────────────────────────────────────

    private suspend fun scoredSubscriptionEpisodes(
        currentPodcast: Podcast,
        exclude: MutableSet<String>,
        recentPodcasts: Set<String>,
        downRankedPodcasts: Set<String>,
        needed: Int
    ): List<QueueEntry> {
        if (needed <= 0) return emptyList()
        val subs = runCatching { sources.getSubscribedPodcasts() }.getOrDefault(emptyList())
        if (subs.isEmpty()) return emptyList()

        val history = runCatching { sources.getRecentHistory(300) }.getOrDefault(emptyList())
        val scores = PodcastScoring.calculateScores(subs.map { it.toScorable() }, history)

        val ranked = subs.asSequence()
            .filter { it.id != currentPodcast.id }
            .filter { !it.title.equals(currentPodcast.title, ignoreCase = true) }
            .filter { it.id !in recentPodcasts }
            .sortedWith(
                compareBy(
                    { it.id in downRankedPodcasts }, // down-ranked shows go last
                    { -(scores[it.id] ?: 0.0) }
                )
            )
            .toList()

        val results = mutableListOf<QueueEntry>()
        var feedFetches = 0
        // Round-robin: one episode per top-scored show for variety.
        for (sub in ranked) {
            if (results.size >= needed) break

            // Cheap path: the cached latest episode avoids a feed fetch entirely.
            val cached = sub.latestEpisode
            if (cached != null && cached.id !in exclude && cached.episodeType != "trailer" &&
                cached.audioUrl.isNotBlank() && cached.id.toLongOrNull() != null
            ) {
                results += cached.toQueueEntry(sub, SmartQueueEngine.SOURCE_SUBSCRIPTION)
                exclude.add(cached.id)
                continue
            }

            if (feedFetches >= MAX_SUBSCRIPTION_FEED_FETCHES) continue
            feedFetches++
            val next = runCatching { sources.getEpisodes(sub.id) }.getOrDefault(emptyList())
                .sortedByDescending { it.publishedDate }
                .firstOrNull {
                    it.id !in exclude && it.episodeType != "trailer" &&
                        it.audioUrl.isNotBlank() && it.id.toLongOrNull() != null
                }
            if (next != null) {
                results += next.toQueueEntry(sub, SmartQueueEngine.SOURCE_SUBSCRIPTION)
                exclude.add(next.id)
            }
        }
        return results
    }

    // ── Tier 3 ─────────────────────────────────────────────────────────────

    private data class Tier3Result(
        val entries: List<QueueEntry>,
        val usedEpisodeSimilar: Boolean
    )

    private suspend fun networkTier3(
        currentEpisode: EpisodeItem,
        currentPodcast: Podcast,
        exclude: MutableSet<String>,
        downRankedPodcasts: Set<String>,
        region: String,
        needed: Int
    ): Tier3Result {
        if (needed <= 0) return Tier3Result(emptyList(), usedEpisodeSimilar = false)

        val history = runCatching { sources.getHistoryForRecommendations(15) }.getOrDefault(emptyList())
        val picks = if (history.isNotEmpty()) {
            personalizedRecommendations(
                currentPodcast = currentPodcast,
                exclude = exclude,
                downRankedPodcasts = downRankedPodcasts,
                region = region,
                history = history,
                needed = needed
            )
        } else if (hasSimilarMetadata(currentEpisode, currentPodcast)) {
            similarToEpisode(
                currentEpisode = currentEpisode,
                currentPodcast = currentPodcast,
                exclude = exclude,
                downRankedPodcasts = downRankedPodcasts,
                region = region,
                needed = needed
            )
        } else {
            emptyList()
        }

        return Tier3Result(
            entries = picks,
            usedEpisodeSimilar = history.isEmpty() && picks.isNotEmpty()
        )
    }

    private suspend fun personalizedRecommendations(
        currentPodcast: Podcast,
        exclude: MutableSet<String>,
        downRankedPodcasts: Set<String>,
        region: String,
        history: List<HistoryItem>,
        needed: Int
    ): List<QueueEntry> {
        val recs = runCatching {
            val interests = sources.getInterests()
            val subs = sources.getSubscribedPodcasts()
            sources.getPersonalizedRecommendations(
                history = history,
                interests = interests,
                country = region,
                subscribedPodcastIds = subs.map { it.id },
                subscribedGenres = subs.map { it.genre }
                    .filter { it.isNotBlank() && it != "Podcast" }
                    .distinct()
            )
        }.getOrDefault(emptyList())

        return networkEpisodePicks(
            episodes = recs,
            currentPodcast = currentPodcast,
            exclude = exclude,
            downRankedPodcasts = downRankedPodcasts,
            needed = needed,
            source = SmartQueueEngine.SOURCE_PERSONALIZED_REC
        )
    }

    private suspend fun similarToEpisode(
        currentEpisode: EpisodeItem,
        currentPodcast: Podcast,
        exclude: MutableSet<String>,
        downRankedPodcasts: Set<String>,
        region: String,
        needed: Int
    ): List<QueueEntry> {
        val similar = runCatching {
            sources.getSimilarEpisodes(
                episodeId = currentEpisode.id.toString(),
                podcastId = currentPodcast.id,
                title = currentEpisode.title.orEmpty(),
                description = currentEpisode.description.orEmpty(),
                podcastTitle = currentPodcast.title,
                country = region
            )
        }.getOrDefault(emptyList())

        return networkEpisodePicks(
            episodes = similar,
            currentPodcast = currentPodcast,
            exclude = exclude,
            downRankedPodcasts = downRankedPodcasts,
            needed = needed,
            source = SmartQueueEngine.SOURCE_SIMILAR_EPISODE
        )
    }

    private fun networkEpisodePicks(
        episodes: List<Episode>,
        currentPodcast: Podcast,
        exclude: MutableSet<String>,
        downRankedPodcasts: Set<String>,
        needed: Int,
        source: String
    ): List<QueueEntry> {
        val picks = episodes.asSequence()
            .filter { it.id !in exclude && it.id.toLongOrNull() != null }
            .filter { it.episodeType != "trailer" && it.audioUrl.isNotBlank() }
            .filter { it.podcastId != currentPodcast.id }
            .sortedBy { (it.podcastId ?: "") in downRankedPodcasts }
            .take(needed)
            .map { it.toQueueEntry(podcastFromEpisode(it), source) }
            .toList()
        picks.forEach { exclude.add(it.episode.id.toString()) }
        return picks
    }

    private fun hasSimilarMetadata(episode: EpisodeItem, podcast: Podcast): Boolean =
        episode.id != 0L &&
            podcast.id.isNotBlank() &&
            !episode.title.isNullOrBlank()

    // ── Tier 3.5: liked-episode similarity boost ───────────────────────────

    private suspend fun likedSimilarBoost(
        exclude: MutableSet<String>,
        region: String
    ): List<QueueEntry> {
        val recentLike = runCatching { sources.getRecentHistory(100) }.getOrDefault(emptyList())
            .filter { it.isLiked }
            .maxByOrNull { it.lastPlayedAt }
            ?: return emptyList()

        val similar = runCatching {
            sources.getSimilarEpisodes(
                episodeId = recentLike.episodeId,
                podcastId = recentLike.podcastId,
                title = recentLike.episodeTitle,
                description = recentLike.episodeDescription ?: "",
                podcastTitle = recentLike.podcastName,
                country = region
            )
        }.getOrDefault(emptyList())

        val pick = similar.firstOrNull {
            it.id !in exclude && it.id.toLongOrNull() != null &&
                it.episodeType != "trailer" && it.audioUrl.isNotBlank()
        } ?: return emptyList()
        exclude.add(pick.id)
        return listOf(pick.toQueueEntry(podcastFromEpisode(pick), SmartQueueEngine.SOURCE_SIMILAR_LIKED))
    }

    // ── Tier 4 ─────────────────────────────────────────────────────────────

    private suspend fun trendingEpisodes(
        currentPodcast: Podcast,
        exclude: MutableSet<String>,
        recentPodcasts: Set<String>,
        downRankedPodcasts: Set<String>,
        region: String,
        needed: Int
    ): List<QueueEntry> {
        if (needed <= 0) return emptyList()

        val genre = resolveGenre(currentPodcast)
        val trending = runCatching { sources.getTrendingPodcasts(region, genre) }.getOrDefault(emptyList())
        if (trending.isEmpty()) return emptyList()

        val recentTrimmed = recentPodcasts.map { it.trim() }.toSet()
        val ordered = trending.sortedBy { it.id in downRankedPodcasts }

        val results = mutableListOf<QueueEntry>()
        var feedFetches = 0
        for (pod in ordered) {
            if (results.size >= needed || feedFetches >= MAX_TRENDING_FEED_FETCHES) break
            if (pod.id == currentPodcast.id) continue
            if (pod.title.equals(currentPodcast.title, ignoreCase = true)) continue
            if (pod.id.trim() in recentTrimmed) continue

            feedFetches++
            val next = runCatching { sources.getEpisodes(pod.id) }.getOrDefault(emptyList())
                .sortedByDescending { it.publishedDate }
                .firstOrNull {
                    it.id !in exclude && it.episodeType != "trailer" &&
                        it.audioUrl.isNotBlank() && it.id.toLongOrNull() != null
                }
            if (next != null) {
                results += next.toQueueEntry(pod, SmartQueueEngine.SOURCE_TRENDING)
                exclude.add(next.id)
            }
        }
        return results
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private suspend fun resolveGenre(currentPodcast: Podcast): String? {
        val genre = currentPodcast.genre
        if (genre.isNotBlank() && genre != "Podcast") return genre
        return runCatching { sources.getPodcastDetails(currentPodcast.id)?.genre }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "Podcast" }
    }

    private fun podcastFromEpisode(episode: Episode): Podcast = Podcast(
        id = episode.podcastId ?: "",
        title = episode.podcastTitle ?: "Podcast",
        artist = episode.podcastArtist ?: "",
        imageUrl = episode.podcastImageUrl ?: episode.imageUrl ?: "",
        genre = episode.podcastGenre ?: "Podcast"
    )

    private fun Episode.toQueueEntry(podcast: Podcast, source: String): QueueEntry {
        val episodeItem = EpisodeItem(
            id = this.id.toLongOrNull() ?: 0L,
            title = this.title,
            description = this.description,
            enclosureUrl = this.audioUrl,
            duration = this.duration,
            datePublished = this.publishedDate,
            image = this.imageUrl,
            feedImage = this.podcastImageUrl ?: podcast.imageUrl,
            episodeType = this.episodeType,
            enclosureType = this.enclosureType,
            chaptersUrl = this.chaptersUrl,
            transcriptUrl = this.transcriptUrl
        )
        return QueueEntry(episode = episodeItem, podcast = podcast, source = source)
    }
}
