package cx.aswin.boxlore.core.ranking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.ranking.database.AdaptiveModelEntity
import cx.aswin.boxlore.core.ranking.database.AdaptiveRankingDatabase
import cx.aswin.boxlore.core.ranking.database.PreferenceFacetEntity
import cx.aswin.boxlore.core.ranking.database.RankingExposureEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveRankingRepositoryTest {
    private lateinit var database: AdaptiveRankingDatabase
    private lateinit var repository: AdaptiveRankingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AdaptiveRankingDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = AdaptiveRankingRepository.create(context, database)
    }

    @After
    fun tearDown() {
        database.close()
        LearningEventLog.configure(false)
        RankingShadowDiagnostics.clear()
    }

    private fun features(showAffinity: Double = 0.0) = CandidateFeatureBuilder.build(CandidateSignals(showAffinity = showAffinity))

    private fun exposure(
        episodeId: String = "ep-1",
        podcastId: String = "pod-1",
        objective: RankingObjective = RankingObjective.DISCOVERY,
        shownAt: Long = 1_000L,
    ) = RankingExposure(
        episodeId = episodeId,
        podcastId = podcastId,
        objective = objective,
        surface = RankingSurface.HOME,
        source = CandidateSource.SERVER_RECOMMENDATION,
        features = features(),
        entryPoint = "home",
        online = true,
        shownAt = shownAt,
    )

    @Test
    fun coldStartScoreReturnsPrior() =
        runTest {
            val score = repository.score(RankingObjective.DISCOVERY, features(), priorScore = 0.5)

            assertEquals(0.5, score.finalScore, 1e-9)
            assertEquals(0.0, score.learnedBlend, 0.0)
            assertEquals(0L, score.updateCount)
        }

    @Test
    fun scoreBatchEmptyReturnsEmpty() =
        runTest {
            assertTrue(repository.scoreBatch(RankingObjective.DISCOVERY, emptyList()).isEmpty())
        }

    @Test
    fun scoreBatchScoresEachInput() =
        runTest {
            val inputs =
                listOf(
                    RankingScoreInput(features(), 0.2),
                    RankingScoreInput(features(), 0.8),
                )

            val scores = repository.scoreBatch(RankingObjective.DISCOVERY, inputs)

            assertEquals(2, scores.size)
            assertEquals(0.2, scores[0].finalScore, 1e-9)
            assertEquals(0.8, scores[1].finalScore, 1e-9)
        }

    @Test
    fun recordExposurePersistsPendingRow() =
        runTest {
            val id = repository.recordExposure(exposure())

            assertTrue(id.isNotBlank())
            val stored = database.adaptiveRankingDao().getAllExposures().single()
            assertEquals(id, stored.exposureId)
            assertNull(stored.resolvedAt)
            assertEquals("ep-1", stored.episodeId)
        }

    @Test
    fun resolveExposureUpdatesModelAndMarksResolved() =
        runTest {
            val id = repository.recordExposure(exposure())

            val resolved = repository.resolveExposure(id, reward = 0.8, listenSeconds = 120, resolvedAt = 5_000L)

            assertTrue(resolved)
            val stored = database.adaptiveRankingDao().getExposure(id)!!
            assertEquals(5_000L, stored.resolvedAt)
            assertEquals(0.8, stored.reward!!, 1e-9)
            assertEquals(120L, stored.listenSeconds)
            val model = database.adaptiveRankingDao().getModel(RankingObjective.DISCOVERY.name)!!
            assertEquals(1L, model.updateCount)
        }

    @Test
    fun resolveExposureUnknownIdReturnsFalse() =
        runTest {
            assertFalse(repository.resolveExposure("missing", reward = 1.0))
        }

    @Test
    fun resolveExposureIsIdempotent() =
        runTest {
            val id = repository.recordExposure(exposure())

            assertTrue(repository.resolveExposure(id, reward = 0.5))
            assertFalse(repository.resolveExposure(id, reward = 0.5))
        }

    @Test
    fun resolveExposureClampsRewardToUnitRange() =
        runTest {
            val id = repository.recordExposure(exposure())

            repository.resolveExposure(id, reward = 5.0)

            assertEquals(1.0, database.adaptiveRankingDao().getExposure(id)!!.reward!!, 1e-9)
        }

    @Test
    fun resolveLatestExposureMatchesMostRecentUnresolved() =
        runTest {
            repository.recordExposure(exposure(episodeId = "ep-9", shownAt = 1L))
            repository.recordExposure(exposure(episodeId = "ep-9", shownAt = 2L))

            assertTrue(repository.resolveLatestExposure("ep-9", reward = 0.3, resolvedAt = 10L))

            val resolvedCount = database.adaptiveRankingDao().getAllExposures().count { it.resolvedAt != null }
            assertEquals(1, resolvedCount)
        }

    @Test
    fun resolveLatestExposureWithoutMatchReturnsFalse() =
        runTest {
            assertFalse(repository.resolveLatestExposure("nobody", reward = 0.3))
        }

    @Test
    fun updateFacetShowStoresAffinity() =
        runTest {
            repository.updateFacet(PreferenceFacetType.SHOW, "pod-42", reward = 1.0, now = 1_000L)

            assertTrue(repository.facetAffinity(PreferenceFacetType.SHOW, "pod-42", now = 1_000L) > 0.0)
        }

    @Test
    fun updateFacetGenreCanonicalizesAlias() =
        runTest {
            repository.updateFacet(PreferenceFacetType.GENRE, "tech", reward = 1.0, now = 1_000L)

            // Alias "tech" is stored under the canonical genre key "Technology".
            val facet = database.adaptiveRankingDao().getFacetsByType(PreferenceFacetType.GENRE.name).single()
            assertEquals("Technology", facet.facetKey)
            assertTrue(repository.genreAffinities(now = 1_000L).containsKey("Technology"))
        }

    @Test
    fun updateFacetGenrePlaceholderIsSkipped() =
        runTest {
            repository.updateFacet(PreferenceFacetType.GENRE, "Podcast", reward = 1.0, now = 1_000L)

            assertTrue(database.adaptiveRankingDao().getAllFacets().isEmpty())
        }

    @Test
    fun updateFacetBlankKeyIsSkipped() =
        runTest {
            repository.updateFacet(PreferenceFacetType.SHOW, "   ", reward = 1.0)

            assertTrue(database.adaptiveRankingDao().getAllFacets().isEmpty())
        }

    @Test
    fun facetAffinityUnknownReturnsZero() =
        runTest {
            assertEquals(0.0, repository.facetAffinity(PreferenceFacetType.SHOW, "unknown"), 0.0)
            assertEquals(0.0, repository.facetAffinity(PreferenceFacetType.SHOW, ""), 0.0)
        }

    @Test
    fun facetAffinitiesReturnsRequestedKeysWithZeroForMissing() =
        runTest {
            repository.updateFacet(PreferenceFacetType.SHOW, "pod-1", reward = 1.0, now = 1_000L)
            val keys =
                setOf(
                    PreferenceFacetKey(PreferenceFacetType.SHOW, "pod-1"),
                    PreferenceFacetKey(PreferenceFacetType.SHOW, "pod-missing"),
                )

            val result = repository.facetAffinities(keys, now = 1_000L)

            assertTrue(result.getValue(PreferenceFacetKey(PreferenceFacetType.SHOW, "pod-1")) > 0.0)
            assertEquals(0.0, result.getValue(PreferenceFacetKey(PreferenceFacetType.SHOW, "pod-missing")), 0.0)
        }

    @Test
    fun facetAffinitiesEmptyKeysReturnsEmpty() =
        runTest {
            assertTrue(repository.facetAffinities(emptySet()).isEmpty())
        }

    @Test
    fun genreAffinitiesReturnsBoundedCanonicalSummary() =
        runTest {
            repository.updateFacet(PreferenceFacetType.GENRE, "Science", reward = 1.0, now = 1_000L)

            val summary = repository.genreAffinities(now = 1_000L)

            assertTrue(summary.containsKey("Science"))
            assertTrue(summary.getValue("Science") in -1.0..1.0)
        }

    @Test
    fun pruneNonCanonicalGenreFacetsMergesAliasesAndDropsPlaceholders() =
        runTest {
            val dao = database.adaptiveRankingDao()
            dao.upsertFacet(PreferenceFacetEntity(PreferenceFacetType.GENRE.name, "tech", 2.0, 0.0, 1_000L))
            dao.upsertFacet(PreferenceFacetEntity(PreferenceFacetType.GENRE.name, "Podcast", 1.0, 0.0, 1_000L))

            repository.pruneNonCanonicalGenreFacets()

            val facets = dao.getFacetsByType(PreferenceFacetType.GENRE.name)
            assertEquals(setOf("Technology"), facets.map { it.facetKey }.toSet())
        }

    @Test
    fun aggregateTelemetryStartsAtColdStart() =
        runTest {
            val telemetry = repository.aggregateTelemetry()

            assertEquals(RankingObjective.entries.size, telemetry.size)
            assertTrue(telemetry.all { it.learningStage == "cold_start" })
            assertTrue(telemetry.all { it.outcomeCountBucket == "0" })
        }

    @Test
    fun aggregateTelemetryReflectsResolvedOutcomes() =
        runTest {
            val id = repository.recordExposure(exposure())
            repository.resolveExposure(id, reward = 0.5)

            val discovery = repository.aggregateTelemetry().single { it.objective == RankingObjective.DISCOVERY.name }
            assertEquals("learning", discovery.learningStage)
            assertEquals("1_9", discovery.outcomeCountBucket)
        }

    @Test
    fun debugSnapshotReportsUpdateCount() =
        runTest {
            val id = repository.recordExposure(exposure())
            repository.resolveExposure(id, reward = 0.5)

            val snapshot = repository.debugSnapshot(RankingObjective.DISCOVERY)

            assertEquals(1L, snapshot.updateCount)
            assertEquals(RankingFeatureSchema.VERSION, snapshot.featureSchemaVersion)
        }

    @Test
    fun exportBackupRoundTripsThroughRestore() =
        runTest {
            val id = repository.recordExposure(exposure())
            repository.resolveExposure(id, reward = 0.7)
            repository.updateFacet(PreferenceFacetType.GENRE, "Science", reward = 1.0, now = 1_000L)

            val backup = repository.exportBackup()
            repository.reset()
            assertTrue(repository.exportBackup().exposures!!.isEmpty())

            repository.restoreBackup(backup)

            val restored = repository.exportBackup()
            assertEquals(backup.exposures!!.size, restored.exposures!!.size)
            assertEquals(backup.facets!!.size, restored.facets!!.size)
            assertEquals(backup.models!!.size, restored.models!!.size)
        }

    @Test
    fun restoreBackupRejectsUnsupportedVersion() =
        runTest {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repository.restoreBackup(AdaptiveRankingBackup(version = 999))
                }
            }
        }

    @Test
    fun restoreBackupRejectsMissingSections() =
        runTest {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    repository.restoreBackup(AdaptiveRankingBackup(models = null))
                }
            }
        }

    @Test
    fun restoreBackupRejectsInvalidFacet() =
        runTest {
            val invalid =
                AdaptiveRankingBackup(
                    models = emptyList(),
                    facets =
                        listOf(
                            PreferenceFacetEntity(
                                facetType = "NOT_A_TYPE",
                                facetKey = "x",
                                positiveEvidence = 1.0,
                                negativeEvidence = 0.0,
                                updatedAt = 0L,
                            ),
                        ),
                    exposures = emptyList(),
                )

            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { repository.restoreBackup(invalid) }
            }
        }

    @Test
    fun resetClearsAllTables() =
        runTest {
            val id = repository.recordExposure(exposure())
            repository.resolveExposure(id, reward = 0.5)
            repository.updateFacet(PreferenceFacetType.SHOW, "pod-1", reward = 1.0)

            repository.reset()

            val dao = database.adaptiveRankingDao()
            assertTrue(dao.getAllExposures().isEmpty())
            assertTrue(dao.getAllFacets().isEmpty())
            assertTrue(dao.getAllModels().isEmpty())
        }

    @Test
    fun learnerInspectorSnapshotSummarizesState() =
        runTest {
            val id = repository.recordExposure(exposure())
            repository.resolveExposure(id, reward = 0.6)
            repository.updateFacet(PreferenceFacetType.GENRE, "Science", reward = 1.0, now = 1_000L)

            val snapshot = repository.learnerInspectorSnapshot(now = 2_000L)

            assertEquals(RankingObjective.entries.size, snapshot.objectives.size)
            assertEquals(RankingFeatureSchema.dimension, snapshot.featureWeights.size)
            assertEquals(1, snapshot.resolvedExposureCount)
            assertEquals(0, snapshot.pendingExposureCount)
            assertTrue(snapshot.facets.any { it.type == PreferenceFacetType.GENRE })
        }

    @Test
    fun resolveExposureRejectsMismatchedFeatureSchema() =
        runTest {
            val features = features(showAffinity = 1.0)
            database.adaptiveRankingDao().insertExposure(
                RankingExposureEntity(
                    exposureId = "stale-schema",
                    episodeId = "ep-stale",
                    podcastId = "pod-1",
                    objective = RankingObjective.DISCOVERY.name,
                    surface = RankingSurface.HOME.name,
                    source = CandidateSource.SERVER_RECOMMENDATION.name,
                    featureSchemaVersion = RankingFeatureSchema.VERSION - 1,
                    featureVector = RankingSerialization.encode(features.values),
                    shownAt = 1_000L,
                    resolvedAt = null,
                    reward = null,
                    listenSeconds = 0,
                    entryPoint = "home",
                    online = true,
                ),
            )

            assertFalse(repository.resolveExposure("stale-schema", reward = 1.0))
            assertNull(database.adaptiveRankingDao().getExposure("stale-schema")!!.resolvedAt)
            assertNull(database.adaptiveRankingDao().getModel(RankingObjective.DISCOVERY.name))
        }

    @Test
    fun scoreDiscardsIncompatiblePersistedModel() =
        runTest {
            val cold = AdaptiveModelState()
            database.adaptiveRankingDao().upsertModel(
                AdaptiveModelEntity(
                    objective = RankingObjective.DISCOVERY.name,
                    featureSchemaVersion = RankingFeatureSchema.VERSION + 1,
                    dimension = cold.dimension,
                    covariance = RankingSerialization.encode(cold.covariance),
                    inverseCovariance = RankingSerialization.encode(cold.inverseCovariance),
                    rewardVector = RankingSerialization.encode(cold.rewardVector),
                    updateCount = 99,
                    updatedAt = 1_000L,
                ),
            )

            val score = repository.score(RankingObjective.DISCOVERY, features(), priorScore = 0.4)

            assertEquals(0.4, score.finalScore, 1e-9)
            assertEquals(0.0, score.learnedBlend, 0.0)
            assertEquals(0L, score.updateCount)
        }

    @Test
    fun resolveThenRescorePrefersTrainedFeaturePattern() =
        runTest {
            val liked = CandidateFeatureBuilder.build(CandidateSignals(showAffinity = 1.0))
            val other = CandidateFeatureBuilder.build(CandidateSignals(showAffinity = 0.0))
            val prior = 0.5
            val objective = RankingObjective.YOUR_SHOWS

            assertEquals(
                repository.score(objective, liked, prior).finalScore,
                repository.score(objective, other, prior).finalScore,
                1e-9,
            )

            repeat(60) { index ->
                val id =
                    repository.recordExposure(
                        RankingExposure(
                            episodeId = "train-$index",
                            podcastId = "pod-liked",
                            objective = objective,
                            surface = RankingSurface.HOME,
                            source = CandidateSource.SERVER_RECOMMENDATION,
                            features = liked,
                            entryPoint = "home",
                            online = true,
                            shownAt = index.toLong(),
                        ),
                    )
                assertTrue(repository.resolveExposure(id, reward = 1.0, listenSeconds = 120))
            }

            val likedScore = repository.score(objective, liked, prior)
            val otherScore = repository.score(objective, other, prior)

            assertTrue(likedScore.learnedBlend > 0.0)
            assertTrue(likedScore.updateCount >= 50L)
            assertTrue(
                "liked=${likedScore.finalScore} other=${otherScore.finalScore}",
                likedScore.finalScore > otherScore.finalScore,
            )
        }
}
