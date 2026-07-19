package cx.aswin.boxlore.core.data.content

import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

enum class ContentDaypart {
    MORNING,
    AFTERNOON,
    EVENING,
    LATE_NIGHT,
}

enum class ContentLayout {
    HERO_CAROUSEL,
    EPISODE_RAIL,
    PODCAST_RAIL,
    MIXED_MASONRY,
    COMPACT_LIST,
    PROTECTED_CARD,
}

enum class ContentRefreshPolicy {
    SESSION,
    MANUAL,
    DAYPART,
    DAILY,
}

data class ContentDurationRange(
    val minimumMinutes: Int,
    val maximumMinutes: Int,
) {
    init {
        require(minimumMinutes >= 0)
        require(maximumMinutes >= minimumMinutes)
    }
}

data class ContentDiversityConstraints(
    val maximumItemsPerShow: Int = 2,
    val minimumDistinctShows: Int = 1,
) {
    init {
        require(maximumItemsPerShow > 0)
        require(minimumDistinctShows >= 0)
    }
}

data class ContentQualityConstraints(
    val minimumSemanticScore: Double = 0.0,
    val unseenShowReserve: Double = 0.0,
) {
    init {
        require(minimumSemanticScore.isFinite())
        require(unseenShowReserve in 0.0..1.0)
    }
}

data class ContentContext(
    val surface: RankingSurface,
    val localMinuteOfDay: Int,
    val weekday: Int,
    val daypart: ContentDaypart,
    val region: String,
    val isDriving: Boolean,
    val isOnline: Boolean,
    val availableMinutes: Int?,
    val currentEpisodeId: String?,
    val currentPodcastId: String?,
    val historyMaturity: Int,
    val subscriptionCount: Int,
    val sessionId: String,
) {
    init {
        require(localMinuteOfDay in 0 until MINUTES_PER_DAY)
        require(weekday in 1..7)
        require(region.isNotBlank())
        require(historyMaturity >= 0)
        require(subscriptionCount >= 0)
        require(sessionId.isNotBlank())
    }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60
    }
}

data class ContentIntent(
    val id: String,
    val objective: RankingObjective,
    val eligibleSurfaces: Set<RankingSurface>,
    val eligibleDayparts: Set<ContentDaypart> = ContentDaypart.entries.toSet(),
    val title: String,
    val subtitle: String? = null,
    val titleKey: String? = null,
    val subtitleKey: String? = null,
    val icon: String? = null,
    val daypartIds: List<String> = emptyList(),
    val providerQueryRef: String? = null,
    val layout: ContentLayout,
    val refreshPolicy: ContentRefreshPolicy = ContentRefreshPolicy.SESSION,
    val minimumItems: Int = 1,
    val maximumItems: Int = 10,
    val freshnessDays: Int? = null,
    val durationRange: ContentDurationRange? = null,
    val diversity: ContentDiversityConstraints = ContentDiversityConstraints(),
    val quality: ContentQualityConstraints = ContentQualityConstraints(),
    val protected: Boolean = false,
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(minimumItems >= 0)
        require(maximumItems >= minimumItems)
        require(freshnessDays == null || freshnessDays > 0)
        require(diversity.minimumDistinctShows <= maximumItems)
    }

    fun isEligible(context: ContentContext): Boolean {
        return context.surface in eligibleSurfaces && context.daypart in eligibleDayparts
    }
}

data class ContentCandidate(
    val id: String,
    val episode: Episode?,
    val podcast: Podcast,
    val source: CandidateSource,
    val intentId: String,
    val retrievalScore: Double,
    val rankingScore: Double = retrievalScore,
    val isNovel: Boolean = false,
    val semanticScore: Double? = episode?.semanticScore,
    val serverRank: Int? = episode?.serverRank,
    val explanationTokens: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank())
        require(intentId.isNotBlank())
        require(retrievalScore.isFinite())
        require(rankingScore.isFinite())
        require(semanticScore == null || semanticScore.isFinite())
        require(serverRank == null || serverRank > 0)
    }
}

data class GroupedContentSection(
    val intent: ContentIntent,
    val items: List<ContentCandidate>,
) {
    init {
        require(items.all { it.intentId == intent.id })
    }
}

data class GroupedContentSections(
    val contractVersion: Int,
    val catalogVersion: String,
    val resolvedDaypart: String,
    val algorithmVersion: String,
    val isFallback: Boolean,
    val generatedAt: String?,
    val sections: List<GroupedContentSection>,
)

data class ContentSection(
    val stableId: String,
    val intent: ContentIntent,
    val items: List<ContentCandidate>,
    val utility: Double,
    val explanation: String? = null,
) {
    init {
        require(stableId.isNotBlank())
        require(items.isNotEmpty())
        require(utility.isFinite())
    }
}

data class ContentSlate(
    val surface: RankingSurface,
    val sessionId: String,
    val sections: List<ContentSection>,
    val generatedAt: Long,
    val catalogVersion: String,
)

interface CandidateProvider {
    val source: CandidateSource

    suspend fun candidates(
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate>
}

interface GroupedCandidateProvider {
    val source: CandidateSource

    suspend fun sections(context: ContentContext): GroupedContentSections?
}

fun interface ContentCandidateRanker {
    suspend fun rank(
        candidates: List<ContentCandidate>,
        intent: ContentIntent,
        context: ContentContext,
    ): List<ContentCandidate>
}
