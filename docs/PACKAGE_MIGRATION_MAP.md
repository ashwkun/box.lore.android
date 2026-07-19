# Package migration map (Phase 2)

Single source of truth for package=module renames and upgrade failsafes.
Konsist / unit tests must stay aligned with this table.

## Module package roots

| Module | Package root |
|:--|:--|
| `:core:prefs` | `cx.aswin.boxlore.core.prefs` |
| `:core:analytics` | `cx.aswin.boxlore.core.analytics` |
| `:core:rss` | `cx.aswin.boxlore.core.rss` |
| `:core:ranking` | `cx.aswin.boxlore.core.ranking` |
| `:core:downloads` | `cx.aswin.boxlore.core.downloads` |
| `:core:playback` | `cx.aswin.boxlore.core.playback` |
| `:core:database` | `cx.aswin.boxlore.core.database` |
| `:core:catalog` | `cx.aswin.boxlore.core.catalog` (PR10) |

## Workers (PR8)

| Old FQCN | New FQCN | Failsafe |
|:--|:--|:--|
| `cx.aswin.boxcast.core.data.SmartDownloadWorker` | `cx.aswin.boxlore.core.downloads.SmartDownloadWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxcast.core.data.AutoDownloadWorker` | `cx.aswin.boxlore.core.downloads.AutoDownloadWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxcast.core.data.PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxlore.core.data.SmartDownloadWorker` | `cx.aswin.boxlore.core.downloads.SmartDownloadWorker` | factory + **stub** at old FQCN |
| `cx.aswin.boxlore.core.data.AutoDownloadWorker` | `cx.aswin.boxlore.core.downloads.AutoDownloadWorker` | factory + **stub** at old FQCN |
| `cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker` | factory + **stub** at old FQCN |

Stubs and `LegacyWorkerFactory` are **permanent** upgrade bridges.

## Services / providers (PR9)

| Old FQCN | New FQCN | Failsafe |
|:--|:--|:--|
| `cx.aswin.boxlore.core.data.service.BoxLorePlaybackService` | `cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService` | Manifest + **stub** |
| `cx.aswin.boxlore.core.data.service.MediaDownloadService` | `cx.aswin.boxlore.core.playback.service.MediaDownloadService` | Manifest + **stub** |
| `cx.aswin.boxlore.core.data.service.AutoCollageProvider` | `cx.aswin.boxlore.core.playback.service.AutoCollageProvider` | Manifest + **stub** |

Room package moves to `cx.aswin.boxlore.core.database` — **no** schema / filename migration (`boxlore_database` unchanged).

## SharedPreferences files (PR8)

| Old file | New file | Opener |
|:--|:--|:--|
| `boxcast_prefs` | `boxlore_prefs` | `BoxcastPrefs` via `PrefsFileMigrator` |
| `boxcast_theme_fast_cache` | `boxlore_theme_fast_cache` | `UserPreferencesRepository` |
| `boxcast_analytics_prefs` | `boxlore_analytics_prefs` | `AnalyticsHelper` |
| `boxcast_player` | `boxlore_player` | `PlaybackRepository` / `PodcastRepository` |
| `boxcast_api_config` | `boxlore_api_config` | `BoxLoreAppRoot` |
| `boxcast_referrer_prefs` | `boxlore_referrer_prefs` | `InstallReferrerManager` |

Key strings inside files are unchanged. Dual-read window applies if copy fails.

## Public types moved in PR8 (representative)

| Old package | New package | Notes |
|:--|:--|:--|
| `…core.data.analytics.*` | `…core.analytics.*` | façade only |
| `…core.data.ranking.*` | `…core.ranking.*` | includes ranking Room types |
| `…core.data.BoxcastPrefs` etc. | `…core.prefs.*` | |
| `…core.data.Rss*` | `…core.rss.*` | |
| `…core.data.ports.DownloadCacheRelinker` | `…core.rss.ports.DownloadCacheRelinker` | |
| `…core.data.DownloadRepository` etc. | `…core.downloads.*` | workers have stubs |
| `…core.data.ports.DownloadServiceLauncher*` | `…core.downloads.ports.*` | |

## ProGuard

Dual `-keep` for `cx.aswin.boxlore.core.data.**` (stubs + remaining modules) and aligned
`core.prefs|analytics|rss|ranking|downloads.**`.

## Tests

- `PrefsFileMigratorTest` — old-only / new-only / both / empty-new+full-old
- `LegacyWorkerFactoryTest` — every alias target is a `ListenableWorker`; old FQCN stubs resolve
- `OldFqcnStubResolvesTest` covered inside `LegacyWorkerFactoryTest.oldFqcnStubsResolve`
