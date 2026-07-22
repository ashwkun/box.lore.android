# `:core:ranking`

## Purpose

Owns adaptive recommendation and candidate scoring: Bayesian facet preferences, online linear models, diversity re-ranking, exposure tracking with exact-token attribution, outcome ledgers, hard show exclusions, reward handling, runtime controls, diagnostics, and the ranking Room database. It does not own the main catalog database, Podcast Index networking, content retrieval orchestration, playback services, or feature UI.

## Public API

- `AdaptiveCandidateScorer` scores podcasts and episodes for home, explore, queue, and downloads.
- `AdaptiveRankingRepository` owns ranking state, exposure recording, exact-token resolution, outcome ledger finalize, facet affinities/confidence, hard show exclusions, backup, and restore.
- `RankingFeedbackRepository` records user actions (including More like this / Not for me / Hide / Anchor selected) and implements `RankingResetPort`. Prefer `FeedbackTarget.exposureId` for attribution.
- `RankingRuntimeControls` exposes runtime toggles for ranking surfaces.
- `RankingObjective`, `RankingSurface`, and `CandidateSource` define scoring context.
- `RankingAction`, `RankingOutcome`, and `RankingReward` define feedback and reward semantics.
- `DiversityPolicy` and `DiversityReranker` shape scored candidate lists.
- `AdaptiveRankingBackup` (v2), `LearningEventLog`, and `RankingShadowDiagnostics` support backup and diagnostics.
- `AdaptiveRankingRepository.exposureResolutionDiagnostics(surface, now)` / `RankingExposureHealthLogic` compute an **observational-only** exact-exposure-token attribution health aggregate (resolution rate, pending-exposure age bucket, unmatched-outcome rate) — see [Quality-observability diagnostics](../../docs/recommendation-system.md#quality-observability-diagnostics-dashboards-only). Distinct from `aggregateTelemetry` (model **learning stage**): this reports attribution **plumbing** health.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/ranking/
  AdaptiveCandidateScorer.kt
  AdaptiveLinearModel.kt
  AdaptiveRankingRepository.kt
  BayesianPreferenceFacet.kt
  DiversityReranker.kt
  LearningEventLog.kt
  RankingExposureHealthLogic.kt
  RankingFeedbackRepository.kt
  RankingModels.kt
  RankingReward.kt
  RankingRuntimeControls.kt
  RankingSerialization.kt
  database/
    AdaptiveRankingDao.kt
    AdaptiveRankingDatabase.kt
    AdaptiveRankingEntities.kt
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

- Room filename `adaptive_ranking_database` (identity — do not rename) stores ranking models, facets, exposures, outcomes, and hard exclusions. Schema version **2** migrates additively from v1 via `MIGRATION_1_2`.
- SharedPreferences file `adaptive_ranking_runtime` stores runtime control values.
- Package root is `cx.aswin.boxlore.core.ranking`.
- Explicit encrypted backup uses `AdaptiveRankingBackup` version 2 (accepts v1 restores without outcomes/exclusions).

## Testing notes

- Unit tests live under `core/ranking/src/test`.
- Coverage includes exact-token resolve, outcome ledger finalize, hard exclusions, facet confidence, and feedback attribution by exposure id.
- `RankingExposureHealthLogicTest` covers the pure bucketing (resolution rate, pending-age bucket, unmatched-outcome rate); `AdaptiveRankingRepositoryTest` covers `exposureResolutionDiagnostics` end-to-end against the DAO.
- Recommendation reset behavior is exercised through `RankingResetPort` fakes in feature tests.

```bash
./gradlew :core:ranking:testDebugUnitTest
./gradlew :core:ranking:ktlintCheck
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
