# `:core:data`

## Purpose

Data layer for repositories, downloads/workers, ranking, RSS, analytics helpers, and prefs. Main Room DB lives in `:core:database` (re-exported via `api`). Playback/queue/Media3 services live in `:core:playback` (same Java packages under `cx.aswin.boxlore.core.data.*`). Further splits: `downloads` / `library` / `prefs` / `analytics`.

## Public API

- Repositories: `PodcastRepository`, `SubscriptionRepository`, `DownloadRepository`, `RssPodcastRepository`, `UserPreferencesRepository`
- Ports: `ports.RssSubscriptionPort`, `ports.RankingResetPort`, `ports.PodcastCatalogPort`, `ports.HistoryRecommendationSource`, `ports.ListeningHistoryBackupPort`
- Shared helpers still here: `QueueMath`, `QueueSkipMemory`, `SmartQueueEngine` / `SmartQueueSources`, `PlaybackSkipBounds` (prefs sanitize without `:core:playback`)
- Managers: `SmartDownloadManager` (uses `HistoryRecommendationSource`, not full `PlaybackRepository`)
- `BoxLoreDatabase` (from `:core:database`, same package)
- Ranking: `AdaptiveCandidateScorer`, `RankingFeedbackRepository`, `AdaptiveRankingRepository` (prefer container façades over ad-hoc `getInstance` in UI); ranking’s adaptive Room DB still lives here under `ranking/database/`
- Workers: `SmartDownloadWorker`, `AutoDownloadWorker`, `PurgeSmartDownloadsWorker` (FQCN stability / aliases matter; smart downloads avoid constructing `PlaybackRepository`)
- Backup: `backup.LibraryBackupManager` (takes `ListeningHistoryBackupPort`, not `PlaybackRepository`)

Playback types (`PlaybackRepository`, `QueueManager`, `QueueRepository`, `BoxLorePlaybackService`, …) are in `:core:playback`.

**Must not** depend on `:core:designsystem` or `:core:playback`. Share UI lives in designsystem; notification seek icons live in this module’s `res/` (consumed by playback services via project dependency).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  ranking/ content/ analytics/ privacy/ backup/ crosspromo/ ports/
```

Main Room sources: `:core:database` → `cx.aswin.boxlore.core.data.database`.

## Dependencies

- → `:core:model`, `:core:network`, `:core:database` (api)
- Media3 exoplayer (offline/cache for `DownloadRepository`), WorkManager, DataStore, Firebase Messaging pieces as needed; Room runtime via `:core:database` (ksp kept for ranking DB)
- Forbidden: → `:core:playback`, → `:core:designsystem`

## Testing notes

- Existing JVM tests under `src/test` (queue math, RSS, ranking, content, smart queue, etc.)
- Playback skip policy tests live in `:core:playback`
- Migrate to JUnit 5 / shared fixtures in `:core:testing` in later phases

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:playback` README](../playback/README.md)
