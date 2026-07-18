# `:core:data`

## Purpose

Data layer for repositories, ranking, and RSS. Implements ports from `:core:domain` (re-exported via `api`). Main Room DB lives in `:core:database` (re-exported via `api`). Prefs live in `:core:prefs` (re-exported via `api`). Analytics helpers (`AnalyticsHelper`, `PendingEntryPoint`, `PlayerSessionAggregator`) live in `:core:analytics` (re-exported via `api`). Playback/queue/Media3 services live in `:core:playback` (same Java packages under `cx.aswin.boxlore.core.data.*`). Download/worker stack lives in `:core:downloads`.

Owns the **shared-deps entry API** for workers (`SharedAppDependencies` / `SharedAppDependenciesHolder`) so background work does not rebuild parallel repository graphs. Download-owned types (`DownloadRepository`, `SmartDownloadManager`) live in `:core:downloads` to avoid a data↔downloads cycle.

## Public API

- Repositories: `PodcastRepository` (ctor-injected `RssPodcastRepository`), `SubscriptionRepository`, `RssPodcastRepository`
- Prefs (from `:core:prefs`, via `api`): `UserPreferencesRepository`, `BoxcastPrefs`
- Domain ports (from `:core:domain`, via `api`): `RssSubscriptionPort`, `RankingResetPort`, `PodcastCatalogPort`, `HistoryRecommendationSource`, `RssSubscriptionResult`
- Data-only port: `ports.ListeningHistoryBackupPort`
- Shared helpers: `QueueMath`, `QueueSkipMemory`, `SmartQueueEngine` / `SmartQueueSources`, `PlaybackSkipBounds`
- Ranking: `AdaptiveCandidateScorer`, `RankingFeedbackRepository`, `AdaptiveRankingRepository` — **production code must not call `getInstance`**; only `AppContainer` installs via `getInstance`
- Composition bridge:
  - `SharedAppDependencies` — interface of Application-scoped instances workers/services need (DB, podcast/subscription/prefs, RSS, ranking, history source). Download-owned types live in `:core:downloads`.
  - `SharedAppDependenciesHolder` — `@Volatile` install + `require()` (throws if unset)
- Port: `ports.DownloadCacheRelinker` — fun interface injected into `RssPodcastRepository` to relink Media3 cache keys when RSS episode IDs change, without a data→downloads compile edge.
- Backup: `backup.LibraryBackupManager` — ranking/RSS via ctor (defaults from holder)

Playback types (`PlaybackRepository`, `QueueManager`, `QueueRepository`, `BoxLorePlaybackService`, …) are in `:core:playback`. Download types (`DownloadRepository`, `SmartDownloadManager`, workers) are in `:core:downloads`.

**Must not** depend on `:core:designsystem`, `:core:playback`, or `:core:downloads`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  SharedAppDependencies.kt   # interface + holder (download types removed — see :core:downloads)
  ranking/ content/ privacy/ backup/ crosspromo/
  ports/                     # DownloadCacheRelinker fun interface
  # analytics/ moved to :core:analytics (same package, re-exported via api)
```

Main Room sources: `:core:database` → `cx.aswin.boxlore.core.data.database`.
Feature-facing ports: `:core:domain` → `cx.aswin.boxlore.core.domain.ports`.

## Dependencies

- → `:core:analytics` (api — re-exports all analytics types transitively)
- → `:core:domain` (api), `:core:prefs` (api), `:core:model`, `:core:network`, `:core:database` (api)
- DataStore (privacy consent still here), Firebase Messaging pieces as needed; Room runtime via `:core:database` (ksp kept for ranking DB)
- Forbidden: → `:core:playback`, → `:core:designsystem`, → `:core:downloads`

## Threading / lifecycle

- Repositories are Application-scoped when obtained from the holder/container
- Workers run on WorkManager executors; they must not construct a second Podcast/ranking/RSS graph
- Room `BoxLoreDatabase.getDatabase` may still be used where a DB-only path is intentional; prefer `deps.database` from the holder in workers

## Persistence & identity

| Stable | Why |
| :--- | :--- |
| Worker FQCNs (moved to `:core:downloads`; package unchanged `cx.aswin.boxlore.core.data`) | Persisted WorkManager requests |
| DataStore `user_preferences` / SharedPrefs `boxcast_prefs` (owned by `:core:prefs`) | Existing installs |
| Main Room DB filename + ranking Room under `ranking/database/` | User data |
| `rss:` podcast IDs | Catalog identity |

## Testing notes

- JVM tests under `src/test` (queue math, RSS, ranking, content, smart queue, **`SharedAppDependenciesHolderTest`**)
- `SharedAppDependenciesHolder.require()` throws when unset (pure unit test; resets holder in `@AfterEach`)
- Playback skip policy tests live in `:core:playback`
- Download holder tests live in `:core:downloads`

```bash
./gradlew :core:data:testDebugUnitTest
```

## CI relevance

Exercised by the unit-test CI job (`testDebugUnitTest` / project suite). Instrumented coverage of workers is local/later.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A1, A3)
- [`:core:downloads` README](../downloads/README.md)
- [`:core:domain` README](../domain/README.md)
- [`:core:playback` README](../playback/README.md)
- [`:app` README](../../app/README.md)
