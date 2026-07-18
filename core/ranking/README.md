# `:core:ranking`

## Purpose

Owns adaptive recommendation and candidate scoring for Boxlore: Bayesian facet preferences, LinUCB linear models, diversity re-ranking, and the action/exposure feedback loop. Includes its **own** Room database (`AdaptiveRankingDatabase`).

Does **not** own main catalog Room (`:core:database`), Podcast Index HTTP (`:core:network`), content orchestration (`:core:data`), or feature UI (debug inspector hooks are consumed by `:feature:home`).

## Public API

| Type | Role |
|---|---|
| `AdaptiveCandidateScorer` | Score podcasts / episodes for home, explore, queue, downloads |
| `AdaptiveRankingRepository` | Model state, exposure recording, facet affinities, backup/restore |
| `RankingFeedbackRepository` | Record actions (play, like, skip, subscribe); implements `RankingResetPort` |
| `RankingRuntimeControls` | Runtime knobs for ranking surfaces |
| `RankingObjective` / `RankingSurface` / `CandidateSource` | Scoring context enums |
| `RankingAction` / `RankingOutcome` / `RankingReward` | Reward calculation |
| `DiversityPolicy` / `DiversityReranker` | Post-scoring diversity pass |
| `AdaptiveRankingBackup` | Portable model state for backup/restore |
| `LearningEventLog` / `RankingShadowDiagnostics` | Session diagnostics |

**Install rule:** `create` + `install` only from `AppContainer`. Workers/services consume ranking via `SharedAppDependenciesHolder.require()`. Do not call production `getInstance` from features.

**Debug surfaces:** `AdaptiveRankingRepository.learnerInspector()`, `RankingShadowDiagnostics.snapshots()`, `LearningEventLog.events`, `RankingRuntimeControls`, `RankingRolloutPolicy`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/ranking/
  AdaptiveCandidateScorer.kt
  AdaptiveRankingRepository.kt / AdaptiveLinearModel.kt
  RankingFeedbackRepository.kt / RankingRuntimeControls.kt
  BayesianPreferenceFacet.kt / RankingModels.kt / RankingReward.kt
  LearningEventLog.kt / RankingSerialization.kt
  database/
    AdaptiveRankingDatabase.kt
    AdaptiveRankingDao.kt / AdaptiveRankingEntities.kt
```

Package stays `cx.aswin.boxlore.core.data.ranking` (Gradle id ≠ package — see `ARCHITECTURE.md`).

## Dependencies

- → `:core:model` (Episode, Podcast, genres, telemetry)
- → `:core:database` (scoring helpers / history entities used by scorers)
- → `:core:domain` (`RankingResetPort`)
- → `:core:prefs` (`BoxcastPrefs` learner-log gate)
- Room + KSP (`AdaptiveRankingDatabase`)

Forbidden: ranking ↛ `:core:data`, features, designsystem. `:core:data` depends on ranking (`api`) and re-exports types.

## Threading / lifecycle

- Application-scoped via `AppContainer` create+install
- Room / model updates on IO; scorers may be called from UI or worker threads — keep calls suspend/`withContext` as existing APIs require
- `LearningEventLog` is process/session StateFlow (not persisted across process death beyond prefs gate)

## Persistence & identity

| Stable | Why |
| :--- | :--- |
| Room filename `adaptive_ranking_database` | Adaptive models / facets / exposures |
| SharedPrefs `adaptive_ranking_runtime` | Runtime controls |
| Package `cx.aswin.boxlore.core.data.ranking` | Opaque refs / import stability |

**Backup exclusion:** app-level backup rules exclude `adaptive_ranking_database` so stale model parameters are not restored across feature-schema versions. See product context in `docs/recommendation-system.md`.

## Testing notes

- JVM: `src/test/.../AdaptiveRankingTest.kt` (pure unit; no Robolectric/instrumented suite required)
- Settings recommendation-reset path covered via `:feature:home` + `RankingResetPort` fakes

```bash
./gradlew :core:ranking:testDebugUnitTest
```

## CI relevance

Exercised by `unit-tests.yml`. Soft future Kover gates for ranking noted in `docs/TESTING.md`.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/recommendation-system.md`](../../docs/recommendation-system.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A5)
- [`:app` README](../../app/README.md) — create+install
- [`:core:data` README](../data/README.md)
- [`:core:domain` README](../domain/README.md)
