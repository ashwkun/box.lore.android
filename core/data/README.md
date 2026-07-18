# `:core:data`

## Purpose

Monolithic data layer: repositories, playback service, downloads/workers, ranking, RSS, analytics helpers, prefs. Main Room DB lives in `:core:database` (re-exported via `api`). Intended to split further into `playback` / `downloads` / `library` / `prefs` / `analytics` in later phases.

## Public API

- Repositories: `PodcastRepository`, `PlaybackRepository`, `QueueRepository`, `SubscriptionRepository`, `DownloadRepository`, `RssPodcastRepository`, `UserPreferencesRepository`
- Ports: `ports.RssSubscriptionPort`, `ports.RankingResetPort`, `ports.PodcastCatalogPort`, `ports.HistoryRecommendationSource`
- Managers: `QueueManager`, `SmartDownloadManager` (uses `HistoryRecommendationSource`, not full `PlaybackRepository`)
- `BoxLoreDatabase` (from `:core:database`, same package), playback `BoxLorePlaybackService`
- Ranking: `AdaptiveCandidateScorer`, `RankingFeedbackRepository`, `AdaptiveRankingRepository` (prefer container façades over ad-hoc `getInstance` in UI); ranking’s adaptive Room DB still lives here under `ranking/database/`
- Workers: `SmartDownloadWorker`, `AutoDownloadWorker`, `PurgeSmartDownloadsWorker` (FQCN stability / aliases matter; smart downloads avoid constructing `PlaybackRepository`)

**Must not** depend on `:core:designsystem`. Share UI lives in designsystem; notification seek icons live in this module’s `res/`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  ranking/ playback/ service/ content/
  analytics/ privacy/ backup/ crosspromo/
```

Main Room sources: `:core:database` → `cx.aswin.boxlore.core.data.database`.

## Dependencies

- → `:core:model`, `:core:network`, `:core:database` (api)
- Media3, WorkManager, DataStore, Coil (artwork in service), Firebase Messaging pieces as needed; Room runtime via `:core:database` (ksp kept for ranking DB)

## Testing notes

- Existing JVM tests under `src/test` (queue math, RSS, ranking, content, etc.)
- Migrate to JUnit 5 / shared fixtures in `:core:testing` in later phases

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
