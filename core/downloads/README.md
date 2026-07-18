# `:core:downloads`

## Purpose

Owns the download stack for Boxlore: `DownloadRepository` (Media3 offline cache + DB sync), `SmartDownloadManager` (mixtape-style auto-download scheduling), `ThrottlingDataSource` (speed-limited download I/O), and the three WorkManager workers that drive background downloads.

Does **not** own playback (Media3 session / `BoxLorePlaybackService` — `:core:playback`), prefs (`:core:prefs`), or catalog/ranking (`:core:data` / `:core:ranking`).

## Public API

- **`DownloadRepository`** — Media3 `DownloadManager` + Room DB sync. Static helpers (`getDownloadManager`, `getDownloadCache`, `getStreamCache`, `relinkDownloadCache`) used by playback services.
- **`SmartDownloadManager`** — mixtape-style auto-download scheduler (`performSync`, `schedulePeriodicSync`, `cancelPeriodicSync`, `purgeAllSmartDownloads`).
- **`ThrottlingDataSource`** + **`DownloadSpeedLimiter`** — speed-limited `DataSource` for background downloads.
- **`DownloadsDependencies`** / **`DownloadsDependenciesHolder`** — Application-scoped download instances; workers call `require()`.
- **`ports.DownloadServiceLauncher`** / **`DownloadServiceLauncherHolder`** — fun-interface bridge so this module can start Media3 `DownloadService` **without** `Class.forName` or a Gradle edge onto `:core:playback`. Installed once from `AppContainer` with `MediaDownloadService::class.java`.

## Worker FQCN table

These FQCNs are persisted in WorkManager's database and must **not** change without extending `LegacyWorkerFactory`:

| Worker class | FQCN |
|---|---|
| `SmartDownloadWorker` | `cx.aswin.boxlore.core.data.SmartDownloadWorker` |
| `AutoDownloadWorker` | `cx.aswin.boxlore.core.data.AutoDownloadWorker` |
| `PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker` |

**Note:** Package stays `cx.aswin.boxlore.core.data` (not `…core.downloads`) so persisted WorkManager requests continue to resolve.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/
  data/                        # package cx.aswin.boxlore.core.data (FQCN-stable)
    DownloadRepository.kt
    SmartDownloadManager.kt
    SmartDownloadWorker.kt / AutoDownloadWorker.kt / PurgeSmartDownloadsWorker.kt
    ThrottlingDataSource.kt
    ports/DownloadServiceLauncher.kt
  downloads/
    DownloadsDependencies.kt   # interface + DownloadsDependenciesHolder
```

## Dependencies

- → `:core:data` (api — catalog/DB/prefs/ranking/domain re-exports)
- → `:core:database`, `:core:domain`, `:core:model` (implementation)
- Media3 exoplayer (DownloadManager, Cache, DataSource), WorkManager

Forbidden reverse edges:

- `:core:data` ↛ `:core:downloads` (cycle)
- `:core:downloads` ↛ `:core:playback` — use `DownloadServiceLauncherHolder` instead of compiling against `MediaDownloadService`

## Threading / lifecycle

- Workers run on WorkManager's default executor threads
- `DownloadRepository` uses `CoroutineScope(Dispatchers.IO)` for DB operations
- `SmartDownloadManager` is Application-scoped; obtain via `DownloadsDependenciesHolder.require()` in workers
- `DownloadServiceLauncherHolder` must be installed before download start requests run

## Persistence & identity

| Stable | Why |
|:---|:---|
| Worker FQCNs (see table above) | Persisted in WorkManager DB across updates |
| Media3 download cache dir `filesDir/downloads` | Cached episode bytes |
| Media3 stream cache dir `cacheDir/stream_cache` | LRU streaming cache |
| `customCacheKey` = `episodeId` | Playback + download cache identity |

## Testing notes

- `DownloadsDependenciesHolderTest` — holder throws when unset
- `SmartDownloadCandidateLogicTest` — pure candidate scoring / quota
- **Workers (Robolectric + `work-testing`):** `SmartDownloadWorkerTest`, `AutoDownloadWorkerTest`

```bash
./gradlew :core:downloads:testDebugUnitTest
./gradlew :core:downloads:testDebugUnitTest --tests 'cx.aswin.boxlore.core.data.SmartDownloadWorkerTest'
```

## CI relevance

Exercised by `unit-tests.yml`. Soft future Kover gates for downloads noted in `docs/TESTING.md`.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A3)
- [`:app` README](../../app/README.md) — `DownloadServiceLauncherHolder` install
- [`:core:data` README](../data/README.md)
- [`:core:playback` README](../playback/README.md)
