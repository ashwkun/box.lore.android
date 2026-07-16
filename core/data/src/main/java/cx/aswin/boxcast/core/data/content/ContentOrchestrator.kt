package cx.aswin.boxcast.core.data.content

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class SharedExposureBudget(
    private val maximumRememberedItems: Int = 200,
    private val maximumItemsPerShow: Int = 2,
) {
    private val exposedItems = linkedMapOf<String, String>()
    private val showCounts = mutableMapOf<String, Int>()

    init {
        require(maximumRememberedItems > 0)
        require(maximumItemsPerShow > 0)
    }

    @Synchronized
    fun allows(candidate: ContentCandidate): Boolean {
        return candidate.id !in exposedItems &&
            (showCounts[candidate.podcast.id] ?: 0) < maximumItemsPerShow
    }

    @Synchronized
    fun record(candidates: Collection<ContentCandidate>) {
        candidates.forEach { candidate ->
            if (candidate.id !in exposedItems) {
                exposedItems[candidate.id] = candidate.podcast.id
                showCounts[candidate.podcast.id] =
                    (showCounts[candidate.podcast.id] ?: 0) + 1
            }
        }
        while (exposedItems.size > maximumRememberedItems) {
            val oldest = exposedItems.entries.first()
            exposedItems.remove(oldest.key)
            val remainingCount = (showCounts[oldest.value] ?: 1) - 1
            if (remainingCount <= 0) showCounts.remove(oldest.value)
            else showCounts[oldest.value] = remainingCount
        }
    }

    @Synchronized
    fun reset() {
        exposedItems.clear()
        showCounts.clear()
    }

    @Synchronized
    fun <T> atomically(block: () -> T): T = block()
}

class SlateComposer {
    fun compose(
        context: ContentContext,
        catalogVersion: String,
        rankedByIntent: List<Pair<ContentIntent, List<ContentCandidate>>>,
        exposureBudget: SharedExposureBudget,
        now: Long = System.currentTimeMillis(),
        preserveSectionOrder: Boolean = false,
    ): ContentSlate = exposureBudget.atomically {
        val proposed = rankedByIntent.mapNotNull { (intent, candidates) ->
            val eligible = selectForIntent(
                intent = intent,
                candidates = candidates,
                exposureBudget = exposureBudget,
                now = now,
            )
            eligible.takeIf { it.isNotEmpty() && it.size >= intent.minimumItems }?.let {
                ContentSection(
                    stableId = "${context.surface.name.lowercase()}:${intent.id}",
                    intent = intent,
                    items = it,
                    utility = sectionUtility(it, context),
                    explanation = it.firstOrNull()?.explanationTokens?.firstOrNull(),
                )
            }
        }
        val orderedSections = if (preserveSectionOrder) {
            proposed
        } else {
            val protected = proposed.filter { it.intent.protected }
            val optional = proposed.filterNot { it.intent.protected }
                .sortedWith(
                    compareByDescending<ContentSection>(ContentSection::utility)
                        .thenBy(ContentSection::stableId),
                )
            protected + optional
        }
        val sections = enforceCrossSectionConstraints(orderedSections)
        sections.forEach { exposureBudget.record(it.items) }
        ContentSlate(
            surface = context.surface,
            sessionId = context.sessionId,
            sections = sections,
            generatedAt = now,
            catalogVersion = catalogVersion,
        )
    }

    private fun selectForIntent(
        intent: ContentIntent,
        candidates: List<ContentCandidate>,
        exposureBudget: SharedExposureBudget,
        now: Long,
    ): List<ContentCandidate> {
        if (intent.maximumItems == 0) return emptyList()
        val eligible = candidates
            .asSequence()
            .distinctBy(ContentCandidate::id)
            .filter(exposureBudget::allows)
            .filter { it.meetsConstraints(intent, now) }
            .toList()
        val selected = mutableListOf<ContentCandidate>()
        val selectedIds = mutableSetOf<String>()
        val perShow = mutableMapOf<String, Int>()

        fun add(candidate: ContentCandidate): Boolean {
            if (candidate.id in selectedIds) return false
            val showCount = perShow[candidate.podcast.id] ?: 0
            if (showCount >= intent.diversity.maximumItemsPerShow) return false
            selected += candidate
            selectedIds += candidate.id
            perShow[candidate.podcast.id] = showCount + 1
            return true
        }

        val unseenReserve = kotlin.math.ceil(
            intent.maximumItems * intent.quality.unseenShowReserve,
        ).toInt()
        for (candidate in eligible) {
            if (candidate.isNovel) add(candidate)
            if (selected.size >= unseenReserve) break
        }
        for (candidate in eligible) {
            if (selected.size >= intent.maximumItems) break
            add(candidate)
        }
        val selectedInRankedOrder = eligible.filter { it.id in selectedIds }
        val distinctShows = selectedInRankedOrder.map { it.podcast.id }.toSet().size
        return selectedInRankedOrder.takeIf {
            it.size >= intent.minimumItems &&
                distinctShows >= intent.diversity.minimumDistinctShows
        }.orEmpty()
    }

    private fun enforceCrossSectionConstraints(
        sections: List<ContentSection>,
    ): List<ContentSection> {
        val seenEpisodes = mutableSetOf<String>()
        val showCounts = mutableMapOf<String, Int>()
        return sections.mapNotNull { section ->
            val pendingShowCounts = showCounts.toMutableMap()
            val pendingEpisodes = seenEpisodes.toMutableSet()
            val constrained = section.items.filter { candidate ->
                val episodeId = candidate.episode?.id ?: candidate.id
                val allowed = episodeId !in pendingEpisodes &&
                    (pendingShowCounts[candidate.podcast.id] ?: 0) < MAX_ITEMS_PER_SHOW_IN_SLATE
                if (allowed) {
                    pendingEpisodes += episodeId
                    pendingShowCounts[candidate.podcast.id] =
                        (pendingShowCounts[candidate.podcast.id] ?: 0) + 1
                }
                allowed
            }
            val distinctShows = constrained.map { it.podcast.id }.toSet().size
            constrained.takeIf {
                it.size >= section.intent.minimumItems &&
                    distinctShows >= section.intent.diversity.minimumDistinctShows
            }?.let {
                it.forEach { candidate ->
                    seenEpisodes += candidate.episode?.id ?: candidate.id
                    showCounts[candidate.podcast.id] =
                        (showCounts[candidate.podcast.id] ?: 0) + 1
                }
                section.copy(items = it)
            }
        }
    }

    private fun sectionUtility(
        items: List<ContentCandidate>,
        context: ContentContext,
    ): Double {
        val topQuality = items.take(3).map(ContentCandidate::rankingScore).averageOrZero()
        val novelty = items.count(ContentCandidate::isNovel).toDouble() / items.size
        val onlineFit = if (context.isOnline) 1.0 else {
            items.count { it.source.name == "DOWNLOADED" }.toDouble() / items.size
        }
        return (topQuality * 0.7 + novelty * 0.2 + onlineFit * 0.1).coerceIn(-1.0, 1.0)
    }

    companion object {
        private const val MAX_ITEMS_PER_SHOW_IN_SLATE = 2
    }
}

private fun ContentCandidate.meetsConstraints(
    intent: ContentIntent,
    nowMillis: Long,
): Boolean {
    val effectiveSemanticScore = semanticScore ?: episode?.semanticScore
    val missingServerSemanticScore =
        effectiveSemanticScore == null &&
            source == cx.aswin.boxcast.core.data.ranking.CandidateSource.SERVER_RECOMMENDATION
    val belowMinimumSemanticScore =
        effectiveSemanticScore?.let { it < intent.quality.minimumSemanticScore } == true
    if (
        intent.quality.minimumSemanticScore > 0.0 &&
        (missingServerSemanticScore || belowMinimumSemanticScore)
    ) {
        return false
    }
    val constrainedEpisode = episode
    intent.freshnessDays?.let { freshnessDays ->
        if (constrainedEpisode == null || constrainedEpisode.publishedDate <= 0L) return false
        val minimumPublishedAt = nowMillis / 1_000L - freshnessDays * SECONDS_PER_DAY
        if (constrainedEpisode.publishedDate < minimumPublishedAt) return false
    }
    intent.durationRange?.let { duration ->
        if (constrainedEpisode == null) return false
        val minimumSeconds = duration.minimumMinutes * SECONDS_PER_MINUTE
        val maximumSeconds = duration.maximumMinutes * SECONDS_PER_MINUTE
        if (constrainedEpisode.duration !in minimumSeconds..maximumSeconds) return false
    }
    return true
}

class ContentOrchestrator(
    private val providers: List<CandidateProvider>,
    private val ranker: ContentCandidateRanker,
    private val groupedProviders: List<GroupedCandidateProvider> = emptyList(),
    private val intentResolver: ContentIntentResolver = ContentIntentResolver(),
    private val slateComposer: SlateComposer = SlateComposer(),
    private val exposureBudget: SharedExposureBudget = SharedExposureBudget(),
) {
    private val sessionCache = ConcurrentHashMap<String, ContentSlate>()

    suspend fun compose(
        context: ContentContext,
        catalog: ContentCatalogSnapshot?,
        forceRefresh: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): ContentSlate {
        val (catalogVersion, intents) = intentResolver.resolve(catalog, context)
        val cacheKey = cacheKey(context, catalogVersion, intents, now)
        if (!forceRefresh) sessionCache[cacheKey]?.let { return it }
        val groupedSections = loadGroupedSections(context)
        if (groupedSections != null) {
            val rankedGroups = supervisorScope {
                groupedSections.sections.map { section ->
                    async {
                        section.intent to rankCandidates(
                            candidates = section.items,
                            intent = section.intent,
                            context = context,
                        )
                    }
                }.map { it.await() }
            }
            val groupedSlate = slateComposer.compose(
                context = context,
                catalogVersion = groupedSections.catalogVersion,
                rankedByIntent = rankedGroups,
                exposureBudget = exposureBudget,
                now = now,
                preserveSectionOrder = true,
            )
            if (groupedSlate.sections.isNotEmpty()) {
                sessionCache[cacheKey] = groupedSlate
                return groupedSlate
            }
        }
        val rankedByIntent = supervisorScope {
            intents.map { intent ->
                async { intent to rankIntent(intent, context) }
            }.map { it.await() }
        }
        return slateComposer.compose(
            context = context,
            catalogVersion = catalogVersion,
            rankedByIntent = rankedByIntent,
            exposureBudget = exposureBudget,
            now = now,
        ).also { sessionCache[cacheKey] = it }
    }

    private suspend fun rankIntent(
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        val candidates = providers.flatMap { provider ->
            providerCandidates(provider, intent, context)
        }.distinctBy(ContentCandidate::id)
        return rankCandidates(candidates, intent, context)
    }

    private suspend fun rankCandidates(
        candidates: List<ContentCandidate>,
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        return try {
            ranker.rank(candidates, intent, context)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            candidates.sortedWith(
                compareByDescending<ContentCandidate>(ContentCandidate::retrievalScore)
                    .thenBy { it.serverRank ?: Int.MAX_VALUE }
                    .thenBy(ContentCandidate::id),
            )
        }
    }

    private suspend fun loadGroupedSections(
        context: ContentContext,
    ): GroupedContentSections? {
        for (provider in groupedProviders) {
            val result = try {
                provider.sections(context)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            if (result != null && result.sections.isNotEmpty()) return result
        }
        return null
    }

    private suspend fun providerCandidates(
        provider: CandidateProvider,
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate> {
        return try {
            provider.candidates(intent, context)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearSession(sessionId: String) {
        sessionCache.keys.removeIf { it.startsWith("$sessionId:") }
    }

    fun clearAll() {
        sessionCache.clear()
        exposureBudget.reset()
    }

    private fun cacheKey(
        context: ContentContext,
        catalogVersion: String,
        intents: List<ContentIntent>,
        now: Long,
    ): String {
        val refreshFingerprint = intents.joinToString(",") { intent ->
            val token = when (intent.refreshPolicy) {
                ContentRefreshPolicy.SESSION -> context.sessionId
                ContentRefreshPolicy.MANUAL -> "manual"
                ContentRefreshPolicy.DAYPART -> context.daypart.name
                ContentRefreshPolicy.DAILY -> (now / DAY_MILLIS).toString()
            }
            "${intent.id}:${intent.refreshPolicy.name}:$token"
        }
        return listOf(
            context.sessionId,
            context.surface.name,
            catalogVersion,
            context.region,
            context.subscriptionCount,
            context.historyMaturity,
            context.currentEpisodeId.orEmpty(),
            refreshFingerprint,
        ).joinToString(":")
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_DAY = 24L * 60L * 60L
