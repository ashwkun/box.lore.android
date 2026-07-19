package cx.aswin.boxlore.core.ranking

import com.google.gson.Gson
import cx.aswin.boxlore.core.ranking.database.AdaptiveModelEntity
import cx.aswin.boxlore.core.ranking.database.PreferenceFacetEntity
import cx.aswin.boxlore.core.ranking.database.RankingExposureEntity
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptiveRankingTest {
    @Test
    fun `feature builder returns finite bounded schema`() {
        val features = CandidateFeatureBuilder.build(
            CandidateSignals(
                showAffinity = 4.0,
                genreAffinity = -2.0,
                ageHours = 24.0,
                progressRatio = 2.0,
                recentExposureCount = 20,
                explicitPreference = -4.0,
            ),
        )

        assertEquals(RankingFeatureSchema.dimension, features.values.size)
        assertTrue(features.values.all(Double::isFinite))
        assertEquals(1.0, features.values[FeatureSlot.SHOW_AFFINITY.ordinal], 0.0)
        assertEquals(0.0, features.values[FeatureSlot.GENRE_AFFINITY.ordinal], 0.0)
        assertEquals(-1.0, features.values[FeatureSlot.EXPLICIT_PREFERENCE.ordinal], 0.0)
        assertTrue(features.values[FeatureSlot.EXPOSURE_FATIGUE.ordinal] < 0.0)
    }

    @Test
    fun `feature schema preserves exact persisted slot order`() {
        assertEquals(
            listOf(
                "INTERCEPT",
                "SHOW_AFFINITY",
                "GENRE_AFFINITY",
                "SOURCE_AFFINITY",
                "FRESHNESS",
                "NOVELTY",
                "DURATION_FIT",
                "SUBSCRIBED",
                "RESUME_PROGRESS",
                "UNPLAYED",
                "SERIAL_MATCH",
                "SERVER_RELEVANCE",
                "EXPOSURE_FATIGUE",
                "TIME_CONTEXT",
                "OFFLINE_SUITABILITY",
                "EXPLICIT_PREFERENCE",
                "RECENT_SUBSCRIPTION",
                "CURRENT_SHOW",
            ),
            FeatureSlot.entries.map(FeatureSlot::name),
        )
        assertEquals(18, RankingFeatureSchema.dimension)
    }

    @Test
    fun `cold start uses legacy prior and grows learned influence gradually`() {
        val model = AdaptiveLinearModel()
        val features = CandidateFeatureBuilder.build(
            CandidateSignals(showAffinity = 1.0, ageHours = null),
        )
        val cold = model.score(
            objective = RankingObjective.DISCOVERY,
            features = features,
            priorScore = 0.7,
            state = AdaptiveModelState(),
        )

        assertEquals(0.7, cold.finalScore, 0.0001)
        assertEquals(0.0, cold.learnedBlend, 0.0)
        assertFalse(cold.contributions.isInitialized())
        cold.contributions.value
        assertTrue(cold.contributions.isInitialized())

        var state = AdaptiveModelState()
        repeat(50) {
            state = model.update(features, reward = 1.0, state = state)
        }
        val learned = model.score(
            objective = RankingObjective.DISCOVERY,
            features = features,
            priorScore = 0.0,
            state = state,
        )
        assertEquals(0.65, learned.learnedBlend, 0.0001)
        assertTrue(learned.learnedScore > 0.0)
        assertTrue(learned.explorationBonus > 0.0)
    }

    @Test
    fun `offline objective never explores`() {
        val model = AdaptiveLinearModel()
        val features = CandidateFeatureBuilder.build(CandidateSignals(isUnseenShow = true))
        var state = AdaptiveModelState()
        repeat(60) {
            state = model.update(features, reward = 0.5, state = state)
        }

        val score = model.score(RankingObjective.OFFLINE, features, 0.0, state)

        assertEquals(0.0, score.explorationBonus, 0.0)
    }

    @Test
    fun `matrix update learns opposite outcomes in opposite directions`() {
        val model = AdaptiveLinearModel()
        val liked = CandidateFeatureBuilder.build(CandidateSignals(showAffinity = 1.0))
        val skipped = CandidateFeatureBuilder.build(CandidateSignals(showAffinity = 0.0))
        var state = AdaptiveModelState()
        repeat(30) {
            state = model.update(liked, 1.0, state)
            state = model.update(skipped, -1.0, state)
        }

        val likedScore = model.score(RankingObjective.YOUR_SHOWS, liked, 0.0, state)
        val skippedScore = model.score(RankingObjective.YOUR_SHOWS, skipped, 0.0, state)
        assertTrue(likedScore.learnedScore > skippedScore.learnedScore)
    }

    @Test
    fun `serialization preserves doubles exactly`() {
        val values = doubleArrayOf(-1.0, 0.0, 0.25, Math.PI)
        val restored = RankingSerialization.decode(
            RankingSerialization.encode(values),
            values.size,
        )

        assertTrue(values.contentEquals(restored))
    }

    @Test
    fun `bayesian facets decay toward neutral and learn both signs`() {
        val day = 24L * 60L * 60L * 1_000L
        val positive = BayesianPreferenceFacet(updatedAt = 0)
            .update(1.0, now = day)
        val negative = positive.update(-1.0, now = day * 2)

        assertTrue(positive.affinity(day) > 0.0)
        assertTrue(negative.affinity(day * 2) < positive.affinity(day))
        assertTrue(
            kotlin.math.abs(positive.affinity(day * 365)) <
                kotlin.math.abs(positive.affinity(day)),
        )
    }

    @Test
    fun `reward is bounded and ignored exposure has no penalty`() {
        assertEquals(0.0, RankingReward.calculate(RankingOutcome()), 0.0)
        assertEquals(
            1.0,
            RankingReward.calculate(
                RankingOutcome(
                    actions = setOf(
                        RankingAction.LIKE,
                        RankingAction.SUBSCRIBE,
                        RankingAction.COMPLETE,
                    ),
                    listenSeconds = 3_600,
                    durationSeconds = 3_600,
                ),
            ),
            0.0,
        )
        assertEquals(
            -1.0,
            RankingReward.calculate(
                RankingOutcome(
                    actions = setOf(
                        RankingAction.EARLY_SKIP,
                        RankingAction.REMOVE_AUTOFILLED,
                    ),
                ),
            ),
            0.0,
        )
    }

    @Test
    fun `diversity reranker removes duplicates caps shows and reserves novelty`() {
        val candidates = listOf(
            RankedCandidate("a", "1", "show-a", "news", 1.0),
            RankedCandidate("duplicate", "1", "show-a", "news", 0.99),
            RankedCandidate("b", "2", "show-a", "news", 0.9),
            RankedCandidate("c", "3", "show-b", "news", 0.8),
            RankedCandidate("novel", "4", "show-c", "science", 0.2, isNovel = true),
        )

        val ranked = DiversityReranker.rerank(
            candidates,
            DiversityPolicy(
                limit = 3,
                maxPerShow = 1,
                reserveNovelSlot = true,
            ),
        )

        assertEquals(3, ranked.size)
        assertEquals(ranked.size, ranked.map { it.episodeId }.distinct().size)
        assertEquals(ranked.size, ranked.map { it.podcastId }.distinct().size)
        assertTrue(ranked.any { it.isNovel })
        assertFalse(ranked.any { it.value == "duplicate" })
    }

    @Test
    fun `novel candidate can replace capped item from the same show`() {
        val ranked = DiversityReranker.rerank(
            candidates = listOf(
                RankedCandidate("top", "1", "show-a", "news", 1.0),
                RankedCandidate("evicted", "2", "show-b", "science", 0.9),
                RankedCandidate("novel", "3", "show-b", "science", 0.1, isNovel = true),
            ),
            policy = DiversityPolicy(
                limit = 2,
                maxPerShow = 1,
                reserveNovelSlot = true,
            ),
        )

        assertEquals(listOf("top", "novel"), ranked.map(RankedCandidate<String>::value))
    }

    @Test
    fun `shadow diagnostics retain only aggregate rank movement`() {
        RankingShadowDiagnostics.clear()

        RankingShadowDiagnostics.record(
            objective = RankingObjective.DISCOVERY,
            priorOrder = listOf("episode-a", "episode-b", "episode-c"),
            adaptiveOrder = listOf("episode-b", "episode-a", "episode-c"),
            now = 123L,
        )

        val snapshot = RankingShadowDiagnostics.snapshots().single()
        assertEquals(RankingObjective.DISCOVERY, snapshot.objective)
        assertEquals(3, snapshot.candidateCount)
        assertEquals(3, snapshot.topFiveOverlap)
        assertEquals(2.0 / 3.0, snapshot.meanAbsoluteRankShift, 0.0001)
        assertEquals(123L, snapshot.recordedAt)
    }

    @Test
    fun `adaptive learning backup survives json round trip`() {
        val model = AdaptiveModelEntity(
            objective = RankingObjective.DISCOVERY.name,
            featureSchemaVersion = RankingFeatureSchema.VERSION,
            dimension = RankingFeatureSchema.dimension,
            covariance = byteArrayOf(1, 2, 3),
            inverseCovariance = byteArrayOf(4, 5, 6),
            rewardVector = byteArrayOf(7, 8),
            updateCount = 42,
            updatedAt = 100,
        )
        val facet = PreferenceFacetEntity("GENRE", "science", 3.0, 1.0, 101)
        val exposure = RankingExposureEntity(
            exposureId = "exposure",
            episodeId = "episode",
            podcastId = "podcast",
            objective = RankingObjective.DISCOVERY.name,
            surface = RankingSurface.HOME.name,
            source = CandidateSource.SERVER_RECOMMENDATION.name,
            featureSchemaVersion = RankingFeatureSchema.VERSION,
            featureVector = byteArrayOf(9, 10),
            shownAt = 102,
            resolvedAt = null,
            reward = null,
            listenSeconds = 0,
            entryPoint = "home",
            online = true,
        )
        val gson = Gson()

        val restored = gson.fromJson(
            gson.toJson(AdaptiveRankingBackup(models = listOf(model), facets = listOf(facet), exposures = listOf(exposure))),
            AdaptiveRankingBackup::class.java,
        )

        assertEquals(1, restored.version)
        assertArrayEquals(model.covariance, restored.models!!.single().covariance)
        assertEquals(facet, restored.facets!!.single())
        assertArrayEquals(exposure.featureVector, restored.exposures!!.single().featureVector)
    }

    @Test
    fun `adaptive rollout starts on home only`() {
        assertTrue(RankingRolloutPolicy.isEnabledByDefault(RankingSurface.HOME))
        RankingSurface.entries.filterNot { it == RankingSurface.HOME }.forEach { surface ->
            assertFalse(RankingRolloutPolicy.isEnabledByDefault(surface))
        }
    }

    @Test
    fun `offline suitability feature is distinct from online duration fit`() {
        val online =
            CandidateFeatureBuilder.build(
                CandidateSignals(durationFit = 0.4, offlineSuitability = 0.4),
            )
        val offline =
            CandidateFeatureBuilder.build(
                CandidateSignals(durationFit = 0.4, offlineSuitability = 1.0),
            )
        assertFalse(online.values.contentEquals(offline.values))
    }
}
