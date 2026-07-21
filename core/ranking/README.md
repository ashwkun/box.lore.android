# `:core:ranking`

## Purpose

Owns adaptive recommendation and candidate scoring: Bayesian facet preferences, online linear models, diversity re-ranking, exposure tracking, reward handling, runtime controls, diagnostics, and the ranking Room database. It does not own the main catalog database, Podcast Index networking, content retrieval orchestration, playback services, or feature UI.

## Public API

- `AdaptiveCandidateScorer` scores podcasts and episodes for home, explore, queue, and downloads.
- `AdaptiveRankingRepository` owns ranking state, exposure recording, facet affinities, hard-hide exclusions, the outcome ledger, backup/restore, and the destructive `ensurePipelineMigrated` gate on `PERSONALIZATION_PIPELINE_VERSION`.
- `RankingFeedbackRepository` records user actions and explicit boosts/penalties (`hideShow`, `moreLikeThis`, `notForMe`, `recordManualAnchor`), applies exact-token exposure attribution with delta reward when a settled exposure receives a later signal, and implements `RankingResetPort`.
- `RankingRuntimeControls` exposes runtime toggles for ranking surfaces.
- `RankingObjective`, `RankingSurface`, and `CandidateSource` define scoring context.
- `RankingAction`, `RankingOutcome`, and `RankingReward` define feedback and reward semantics (including `MORE_LIKE_THIS`, `NOT_FOR_ME`, `HIDE_SHOW`, `MANUAL_ANCHOR`).
- `BayesianPreferenceFacet.belief(now)` and `FacetBelief(affinity, confidence, evidence)` expose evidence-weighted taste readings.
- `DiversityPolicy` and `DiversityReranker` shape scored candidate lists.
- `AdaptiveRankingBackup`, `LearningEventLog`, and `RankingShadowDiagnostics` support backup and diagnostics.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/ranking/
  AdaptiveCandidateScorer.kt
  AdaptiveLinearModel.kt
  AdaptiveRankingRepository.kt
  BayesianPreferenceFacet.kt
  DiversityReranker.kt
  LearningEventLog.kt
  RankingFeedbackRepository.kt
  RankingModels.kt
  RankingReward.kt
  RankingRuntimeControls.kt
  RankingSerialization.kt
  database/
    AdaptiveRankingDao.kt
    AdaptiveRankingDatabase.kt
    AdaptiveRankingEntities.kt  # Model, facet, exposure, hard exclusion, and outcome ledger entities
```

## Dependencies

- Project dependencies: `:core:model`, `:core:database`, `:core:domain`, and `:core:prefs`.
- Libraries: Room runtime, Room KTX, Room compiler through KSP, AndroidX core, coroutines, and Gson in tests.
- Reverse-edge rule: ranking must not depend on catalog, playback, downloads, designsystem, analytics, or feature modules.

## Threading / lifecycle

- Production ranking instances are application-scoped and installed from `AppContainer`.
- Room and model updates use background dispatchers through suspend APIs.
- Scorers may be called from UI, service, or worker paths; keep scoring APIs suspend where they touch persisted state.
- `LearningEventLog` is process/session state and is not a durable event log. Capture is gated at app start via `BoxcastPrefs.resolveLearnerLogEnabled`: on by default in debug; **always off in release** unless the user explicitly opts in on the debug screen.

## Persistence & identity

- Room filename `adaptive_ranking_database` stores ranking models, facets, exposures, hard-hide exclusions, and the outcome ledger. Schema version 2 uses `fallbackToDestructiveMigration(dropAllTables = true)` — personalization state is disposable across the Home rebuild.
- SharedPreferences file `adaptive_ranking_runtime` stores runtime control values and the `personalization_pipeline_version` key gating one-shot pipeline wipes.
- Package root is `cx.aswin.boxlore.core.ranking`.
- App backup rules exclude the adaptive database from automatic platform backup; explicit encrypted backup support is handled through ranking backup models.

## Testing notes

- Unit tests live under `core/ranking/src/test`.
- `AdaptiveRankingTest` covers cold-start blending, objective behavior, and opposite-outcome learning.
- Recommendation reset behavior is exercised through `RankingResetPort` fakes in feature tests.

```bash
./gradlew :core:ranking:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs ranking JVM tests.
- Ranking participates in app and feature test compilation anywhere scoring or diagnostics are wired.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/recommendation-system.md`](../../docs/recommendation-system.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:domain` README](../domain/README.md)
