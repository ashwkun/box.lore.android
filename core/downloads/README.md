# `:core:downloads`

## Purpose

Owns the download stack for Boxlore: `DownloadRepository` (Media3 offline cache + DB sync), `SmartDownloadManager` (mixtape-style auto-download scheduling), `ThrottlingDataSource` (speed-limited download I/O), and the three WorkManager workers that drive background downloads.

Does **not** own playback (Media3 session / `BoxLorePlaybackService` — `:core:playback`), prefs (`:core:prefs`), or catalog/ranking (`:core:catalog` / `:core:ranking`).

## Public API

- **`DownloadRepository`** — Media3 `DownloadManager` + Room DB sync. Static helpers (`getDownloadManager`, `getDownloadCache`, `getStreamCache`, `relinkDownloadCache`) used by playback services.
- **`SmartDownloadManager`** — mixtape-style auto-download scheduler (`performSync`, `schedulePeriodicSync`, `cancelPeriodicSync`, `purgeAllSmartDownloads`).
- **`ThrottlingDataSource`** + **`DownloadSpeedLimiter`** — speed-limited `DataSource` for background downloads.
- **`DownloadsDependencies`** / **`DownloadsDependenciesHolder`** — Application-scoped download instances; workers call `require()`.
- **`ports.DownloadServiceLauncher`** / **`DownloadServiceLauncherHolder`** — fun-interface bridge so this module can start Media3 `DownloadService` **without** `Class.forName` or a Gradle edge onto `:core:playback`. Installed once from `AppContainer` with `MediaDownloadService::class.java`.

## Worker FQCN table

Canonical implementations live under `cx.aswin.boxlore.core.downloads`. Old FQCNs remain via stubs + `LegacyWorkerFactory` (permanent upgrade bridges). See [`docs/PACKAGE_MIGRATION_MAP.md`](../../docs/PACKAGE_MIGRATION_MAP.md).

| Worker class | Canonical FQCN | Old FQCN (stub) |
|---|---|---|
| `SmartDownloadWorker` | `cx.aswin.boxlore.core.downloads.SmartDownloadWorker` | `cx.aswin.boxlore.core.data.SmartDownloadWorker` |
| `AutoDownloadWorker` | `cx.aswin.boxlore.core.downloads.AutoDownloadWorker` | `cx.aswin.boxlore.core.data.AutoDownloadWorker` |
| `PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker` |

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/
  downloads/                   # package cx.aswin.boxlore.core.downloads
    DownloadRepository.kt
    SmartDownloadManager.kt
    SmartDownloadWorker.kt / AutoDownloadWorker.kt / PurgeSmartDownloadsWorker.kt
    ThrottlingDataSource.kt
    DownloadsDependencies.kt
    ports/DownloadServiceLauncher.kt
  data/                        # permanent stubs at old FQCNs
    SmartDownloadWorker.kt / AutoDownloadWorker.kt / PurgeSmartDownloadsWorker.kt
```

## Package root

`cx.aswin.boxlore.core.downloads` (matches module).
