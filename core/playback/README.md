# `:core:playback`

## Purpose

Owns playback session control, queue orchestration, Media3 services (player + offline download foreground service + Android Auto collage provider), and **smart-queue ownership** (`SmartQueueEngine`, `SmartQueueSources`, `QueueMath`, `QueueSkipMemory`, `MixtapeEngine`). Deliberately does **not** own Room schemas, prefs DataStore, ranking Room, RSS, or smart-download workers (those live in `:core:database` / `:core:prefs` / `:core:ranking` / `:core:rss` / `:core:downloads`).

## Public API

Stable types/entry points other modules may depend on:

- `PlaybackRepository` — one UI/session instance; ctor-injected `RankingFeedbackRepository` (never call ranking `getInstance` here)
- `QueueRepository` / `QueueManager` — queue persistence + explicit play/add orchestration
- `QueueMath` / `QueueSkipMemory` / `SmartQueueEngine` / `SmartQueueSources` / `MixtapeEngine` — smart-queue helpers (packages remain `cx.aswin.boxlore.core.data` until PR8–10)
- `playback.PlaybackMediaIdPolicy` / `PlaybackArtworkResolver` — media-id encoding and artwork URL resolution
- `playback.PlaybackSkipPolicy` — intro/outro trim and seek policy
- `playback.HistoryRecommendationLogic` — pure eligibility filter for recommendation history
- `playback.AutoVoiceSearchLogic` / `SmartQueueRefillPolicy` / `MixtapeResumePolicy` / `NightWindowLogic` / `ListeningHistoryUpsertLogic`
- `playback.PlaybackIntroOutroController` — intro skip / outro trim lifecycle
- `service.SmartQueueRefillCoordinator` — service-owned Smart Queue auto-refill
- `service.auto.AutoBrowseLibraryCallback` + `AutoBrowseLibraryHost` — Android Auto browse
- `PlaybackRepository.getRecentHistoryList(limit)` — scoring/history slices without feature → DAO access
- `service.CoilBitmapLoader` — Media3 bitmap loader (extracted from the playback service)
- Services (FQCN must stay stable across releases):
  - `cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService` — uses `SharedAppDependenciesHolder.require()` for podcast/ranking/download/prefs (no parallel graph)
  - `cx.aswin.boxlore.core.playback.service.MediaDownloadService`
  - `cx.aswin.boxlore.core.playback.service.AutoCollageProvider`

Packages remain under `cx.aswin.boxlore.core.data.*` for AndroidManifest / WorkManager / MediaSession stability.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  PlaybackRepository.kt
  QueueManager.kt / QueueRepository.kt
  QueueMath.kt / QueueSkipMemory.kt
  SmartQueueEngine.kt / SmartQueueSources.kt / MixtapeEngine.kt
  playback/     # PlaybackSkipPolicy, HistoryRecommendationLogic, refill policy, …
  service/      # BoxLorePlaybackService, MediaDownloadService, AutoCollage*, SmartQueueRefillCoordinator
  service/auto/ # Android Auto browse helpers
```

Android Auto browse session callbacks live in `service/auto/`; `BoxLorePlaybackService` implements `AutoBrowseLibraryHost` and delegates `MediaLibrarySession.Callback` behavior to `AutoBrowseLibraryCallback`.

## Dependencies

Gradle edges (project + notable libs):

- → `:core:catalog` (api), `:core:database`, `:core:network`, `:core:model`
- → `:core:ranking` (implementation), `:core:analytics` (implementation), `:core:downloads` (implementation)
- Media3 exoplayer / session / ui
- Coil (session artwork), coroutines (+ guava await)

Forbidden reverse edges: `:core:catalog` ↛ `:core:playback`; `:core:downloads` ↛ `:core:playback` (`:core:downloads` starts `MediaDownloadService` via `DownloadServiceLauncherHolder` installed by `:app`, not a Gradle edge). Backup/history seams use `ports.ListeningHistoryBackupPort`.

## Threading / lifecycle

- `PlaybackRepository` is Application-scoped via `AppContainer`
- `BoxLorePlaybackService` is a Media3 `MediaLibraryService`; it resolves shared deps lazily on first use (holder must be installed in `Application.onCreate`)
- Player callbacks on main; IO work via service coroutine scopes

## Persistence & identity

| Stable | Why |
| :--- | :--- |
| `BoxLorePlaybackService` / `MediaDownloadService` Manifest names | System bindings |
| mediaId prefixes (`episode:`, `queue:`, `learn:`) | Session / Auto contracts |
| SharedPrefs `boxcast_player` | Player session flags |
| Device UUID prefs key `device_uuid` | Stable install id — **do not log the raw value** |

## Testing notes

- JVM unit tests under `src/test` (`PlaybackSkipPolicyTest`, `PlaybackMediaIdPolicyTest`, `PlaybackArtworkResolverTest`, `HistoryRecommendationLogicTest`, `AutoVoiceSearchLogicTest`, `SmartQueueRefillPolicyTest`, `MixtapeResumePolicyTest`, `NightWindowLogicTest`, `ListeningHistoryUpsertLogicTest`, `QueueMathTest`, `QueueSkipMemoryTest`, `SmartQueueEngineTest`)
- Prefer fakes from `:core:testing` for broader playback graph tests in later phases
- Service depends on holder install for process-level tests

```bash
./gradlew :core:playback:testDebugUnitTest
```

## CI relevance

Unit tests run with the project suite in CI. Service/Auto paths are primarily device/emulator exercised.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A1, A3)
- [`:core:downloads` README](../downloads/README.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:app` README](../../app/README.md)
