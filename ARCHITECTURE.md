# Boxlore architecture

Reference for how the Android app is structured: module boundaries, dependency direction, composition root, and upgrade-safe identity. Module-local detail lives in each folder’s `README.md`.

## Overview

Boxlore is a multi-module Gradle project. `:app` is the application shell. Shared behavior lives under `:core:*`. Screen and flow UI lives under `:feature:*`. There is no Hilt or Koin: a single `AppContainer` is the composition root.

The graph is layered so playback and features depend inward on catalog and lower cores. Features do not depend on other features. Catalog does not depend on the design system. Feature modules never talk to PostHog directly; they use `:core:analytics`.

## Identity and storage

These values are part of the shipping product. Renames or recreations break upgrades, WorkManager, Media3, and deep links.

| Concern | Contract |
| :--- | :--- |
| `applicationId` | `cx.aswin.boxlore` |
| Code packages / Android namespaces | `cx.aswin.boxlore.*` |
| SharedPreferences **files** | Opened as `boxlore_*`; `PrefsFileMigrator` copies from legacy `boxcast_*` when needed. **Key** strings inside those files stay stable. |
| DataStore | File name `user_preferences` |
| Room (main) | Database filename and tables stay stable |
| Room (ranking) | Separate adaptive database owned by `:core:ranking` |
| WorkManager | `LegacyWorkerFactory` plus permanent `core.data.*` stubs resolve historical FQCNs |
| Deep links | `boxlore://` and `boxcast://`, plus both HTTPS share-path prefixes |
| BuildConfig | Prefer `BOXLORE_*`; Gradle still dual-reads `BOXCAST_*` fallbacks |
| Episode / media IDs | `rss:` prefixes, negative RSS IDs, mediaId prefixes, and `customCacheKey` schemes stay as implemented |
| Playback instance | One UI-scoped `PlaybackRepository`; routes and workers must not construct a second one |
| Object graph order | DB → `PodcastRepository` → `QueueRepository` → `PlaybackRepository` → `QueueManager` → `SmartDownloadManager` |
| Smart Queue refill | Owned by `BoxLorePlaybackService` only |

## Module map

```text
:app
:core:model | :core:network | :core:domain | :core:database | :core:prefs
:core:analytics | :core:catalog | :core:rss | :core:downloads | :core:playback | :core:ranking
:core:designsystem | :core:testing
:feature:home | :feature:player | :feature:info | :feature:explore
:feature:library | :feature:onboarding | :feature:briefing
```

On disk, the folder path matches the Gradle id (`core/playback` → `:core:playback`).

### Ownership

| Module | Responsibility | README |
| :--- | :--- | :--- |
| `:app` | `Application`, `AppContainer`, navigation host, FCM, `WorkerFactory` | [`app/README.md`](app/README.md) |
| `:core:model` | Shared models and enums | [`core/model/README.md`](core/model/README.md) |
| `:core:network` | HTTP client (`BoxLoreApi` / `NetworkModule`) and network DTOs | [`core/network/README.md`](core/network/README.md) |
| `:core:domain` | Thin ports and small result types (no Room or repositories) | [`core/domain/README.md`](core/domain/README.md) |
| `:core:database` | Main Room database, entities, DAOs, migrations | [`core/database/README.md`](core/database/README.md) |
| `:core:prefs` | DataStore and SharedPreferences façades (`UserPreferencesRepository`, `BoxcastPrefs`) | [`core/prefs/README.md`](core/prefs/README.md) |
| `:core:analytics` | Analytics façade (`AnalyticsHelper`, `Analytics`, `RecordingAnalytics`); PostHog init stays in `:app`. Event names and properties: [`docs/ANALYTICS_EVENT_GLOSSARY.md`](docs/ANALYTICS_EVENT_GLOSSARY.md) | [`core/analytics/README.md`](core/analytics/README.md) |
| `:core:catalog` | Catalog orchestration: `PodcastRepository`, subscriptions, content sections, backup/restore | [`core/catalog/README.md`](core/catalog/README.md) |
| `:core:rss` | RSS fetch/parse, `RssPodcastRepository`, `rss:` / negative IDs | [`core/rss/README.md`](core/rss/README.md) |
| `:core:ranking` | Adaptive scoring, LinUCB, feedback, `AdaptiveRankingDatabase` | [`core/ranking/README.md`](core/ranking/README.md) |
| `:core:downloads` | `DownloadRepository`, Smart Downloads, related WorkManager workers | [`core/downloads/README.md`](core/downloads/README.md) |
| `:core:playback` | `PlaybackRepository`, queue, Media3 services, smart-queue helpers | [`core/playback/README.md`](core/playback/README.md) |
| `:core:designsystem` | Theme and shared composables; no data or network ownership | [`core/designsystem/README.md`](core/designsystem/README.md) |
| `:core:testing` | Shared fixtures, dispatcher helpers, architecture guards | [`core/testing/README.md`](core/testing/README.md) |
| `:feature:home` | Home, Settings hub, Add RSS, Debug | [`feature/home/README.md`](feature/home/README.md) |
| `:feature:player` | Player overlay (`PlayerSheetScaffold`); not a NavHost destination | [`feature/player/README.md`](feature/player/README.md) |
| `:feature:info` | Podcast and episode detail, including deep links | [`feature/info/README.md`](feature/info/README.md) |
| `:feature:explore` | Explore plus Learn / LearnHistory | [`feature/explore/README.md`](feature/explore/README.md) |
| `:feature:library` | Library hub, subscriptions, downloads, history, liked | [`feature/library/README.md`](feature/library/README.md) |
| `:feature:onboarding` | First-run flows (AI, genre, search, import) | [`feature/onboarding/README.md`](feature/onboarding/README.md) |
| `:feature:briefing` | Daily briefing screen | [`feature/briefing/README.md`](feature/briefing/README.md) |

### Core package roots

| Module | Package |
| :--- | :--- |
| `:core:prefs` | `cx.aswin.boxlore.core.prefs` |
| `:core:analytics` | `cx.aswin.boxlore.core.analytics` |
| `:core:rss` | `cx.aswin.boxlore.core.rss` |
| `:core:ranking` | `cx.aswin.boxlore.core.ranking` |
| `:core:downloads` | `cx.aswin.boxlore.core.downloads` |
| `:core:playback` | `cx.aswin.boxlore.core.playback` |
| `:core:database` | `cx.aswin.boxlore.core.database` |
| `:core:catalog` | `cx.aswin.boxlore.core.catalog` |

## Dependency direction

```mermaid
flowchart TB
  app[:app]
  subgraph features [feature]
    home[:feature:home]
    player[:feature:player]
    info[:feature:info]
    explore[:feature:explore]
    library[:feature:library]
    onboarding[:feature:onboarding]
    briefing[:feature:briefing]
  end
  playback[:core:playback]
  catalog[:core:catalog]
  rss[:core:rss]
  analytics[:core:analytics]
  downloads[:core:downloads]
  ranking[:core:ranking]
  prefs[:core:prefs]
  domain[:core:domain]
  database[:core:database]
  network[:core:network]
  design[:core:designsystem]
  model[:core:model]

  app --> features
  app --> playback
  app --> catalog
  app --> downloads
  app --> design
  features --> playback
  features --> catalog
  features --> downloads
  features --> design
  features --> model
  playback --> catalog
  playback --> downloads
  downloads --> catalog
  catalog --> rss
  catalog --> prefs
  catalog --> domain
  catalog --> database
  catalog --> ranking
  catalog --> network
  catalog --> model
  features --> analytics
  features --> ranking
  playback --> ranking
  playback --> analytics
  design --> analytics
  rss --> database
  rss --> domain
  rss --> model
  ranking --> database
  ranking --> domain
  ranking --> prefs
  ranking --> model
  analytics --> model
  prefs --> model
  domain --> network
  domain --> model
  database --> network
  database --> model
  design --> model
  network --> model
```

The primary runtime stack is **playback → catalog → prefs / domain / database / network / model**.

### Rules enforced in the graph

- Features do not declare Gradle dependencies on other features (Konsist).
- `:core:playback` depends on `:core:catalog`; catalog does not depend on playback.
- `:core:catalog` does not depend on `:core:designsystem`.
- Features that need analytics or ranking depend on `:core:analytics` / `:core:ranking` directly. Catalog does not re-export analytics; ranking is not an `api` edge from catalog.
- Feature sources do not import PostHog (`scripts/ci/check-feature-no-posthog.sh`).
- Enums shared by catalog and UI (for example `AutoTranscriptState`) live in `:core:model`.
- `:core:domain` holds ports; `:core:catalog` implements catalog-facing ports.
- `:core:network` is HTTP/API only. `RssFeedClient` lives in `:core:rss` and reaches callers through `:core:catalog`’s `api(rss)` edge.

## Composition root

`AppContainer` (in `:app`) owns the shared object graph and is created only in `BoxLoreApplication`. `MainActivity`, `BoxLoreNavHost`, and feature assemblers read `application.container`. They do not build repositories or a second graph.

Home, Settings, and Info ViewModels are built through assemblers (`HomeViewModelAssembler`, `SettingsViewModelAssembler`, `InfoViewModelAssembler`). Narrow ports under `core.domain.ports` let ViewModels and workers take fakes without depending on full repositories or `BoxLoreDatabase`. Production wiring uses `RoomLocalCatalog` and `RoomEpisodeOfflineLookup` from `AppContainer`. `ListeningHistoryBackupPort` lives in `core.catalog.ports`.

## Product surfaces

| Surface | Module | Notes |
| :--- | :--- | :--- |
| Home, Settings hub, Add RSS | `:feature:home` | Settings hosts the RSS dialog |
| Learn / LearnHistory | `:feature:explore` | Learn is a bottom-nav tab |
| Player overlay | `:feature:player` | `PlayerSheetScaffold`, not a NavHost route |
| Podcast / episode detail | `:feature:info` | Dual episode routes and deep links |
| Playback, queue, Media3 | `:core:playback` | Includes permanent `core.data.service` stubs |
| HTTP API | `:core:network` | Separate from RSS |
| Ranking | `:core:ranking` | Own adaptive Room database |
| RSS catalog | `:core:rss` | Negative / `rss:` IDs; exposed through catalog |

## Upgrade failsafes

Historical FQCNs and preference file names remain reachable so upgrades from older installs keep working. These bridges are permanent.

### Workers

| Historical FQCN | Implementation | Bridge |
| :--- | :--- | :--- |
| `cx.aswin.boxcast.core.data.SmartDownloadWorker` | `cx.aswin.boxlore.core.downloads.SmartDownloadWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxcast.core.data.AutoDownloadWorker` | `cx.aswin.boxlore.core.downloads.AutoDownloadWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxcast.core.data.PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxlore.core.data.SmartDownloadWorker` | `cx.aswin.boxlore.core.downloads.SmartDownloadWorker` | factory + stub |
| `cx.aswin.boxlore.core.data.AutoDownloadWorker` | `cx.aswin.boxlore.core.downloads.AutoDownloadWorker` | factory + stub |
| `cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker` | factory + stub |

### Services / providers

| Historical FQCN | Implementation | Bridge |
| :--- | :--- | :--- |
| `cx.aswin.boxlore.core.data.service.BoxLorePlaybackService` | `cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService` | Manifest + stub |
| `cx.aswin.boxlore.core.data.service.MediaDownloadService` | `cx.aswin.boxlore.core.playback.service.MediaDownloadService` | Manifest + stub |
| `cx.aswin.boxlore.core.data.service.AutoCollageProvider` | `cx.aswin.boxlore.core.playback.service.AutoCollageProvider` | Manifest + stub |

### SharedPreferences files

| Legacy file | Current file | Opened by |
| :--- | :--- | :--- |
| `boxcast_prefs` | `boxlore_prefs` | `BoxcastPrefs` via `PrefsFileMigrator` |
| `boxcast_theme_fast_cache` | `boxlore_theme_fast_cache` | `UserPreferencesRepository` |
| `boxcast_analytics_prefs` | `boxlore_analytics_prefs` | `AnalyticsHelper` |
| `boxcast_player` | `boxlore_player` | `PlaybackRepository` / `PodcastRepository` |
| `boxcast_api_config` | `boxlore_api_config` | `BoxLoreAppRoot` |
| `boxcast_referrer_prefs` | `boxlore_referrer_prefs` | `InstallReferrerManager` |

ProGuard keeps the permanent `core.data.**` stubs alongside `core.catalog|prefs|analytics|rss|ranking|downloads|playback|database.**`.

## Source file size

Kotlin sources under `*/src/main/**` stay under **1000 lines**. Larger units are split into same-package helpers or composables without changing public APIs or the identity/FQCN contracts above.

## Verification

How the tree is tested — commands, coverage floors, and layer status — is in [`docs/TESTING.md`](docs/TESTING.md).

| Layer | Role | Where |
| :--- | :--- | :--- |
| JVM unit | Logic, ports, ViewModel slices | [`docs/TESTING.md`](docs/TESTING.md) |
| Compose UI | Controls, tags, navigation wiring | [`docs/TESTING.md`](docs/TESTING.md) |
| Maestro | Device E2E smoke | [`maestro/README.md`](maestro/README.md) |
| Architecture-as-code | Konsist and filesystem guards | `:core:testing` |
| Screenshots | Visual baselines | [`docs/screenshots/README.md`](docs/screenshots/README.md) |

Shared test fixtures live in `:core:testing`. The project does not use MockK or Hilt.

## Related documents

| Document | Contents |
| :--- | :--- |
| [`docs/MODULE_README_TEMPLATE.md`](docs/MODULE_README_TEMPLATE.md) | Module README shape |
| [`docs/recommendation-system.md`](docs/recommendation-system.md) | Ranking and recommendation behavior |
| [`maestro/README.md`](maestro/README.md) | Local Maestro flows |
