# `:core:playback`

## Purpose

Owns playback session control, queue orchestration, smart queue logic, Media3 playback services, the offline download foreground service, and Android Auto browse/collage support. It does not own Room schemas, preference storage, RSS parsing, ranking persistence, smart-download workers, or feature UI.

## Public API

- `PlaybackRepository` exposes player/session operations to app and feature UI (history ports via
  class delegation to `PlaybackHistoryStore`; queue / transport / sleep / history helpers via
  same-package extension API files).
- `QueueRepository` and `QueueManager` persist and orchestrate explicit queue operations. `QueueManager` emits `queue_modified` add only after `PlaybackRepository.addToQueue` returns success.
- `PlaybackQueueCoordinator` emits `queue_modified` remove on remove; `undoQueueRemoval` emits a compensating `add` (`source=undo`) so undone removals do not permanently skew analytics.
- `QueueMath`, `QueueSkipMemory`, `SmartQueueEngine`, `SmartQueueSources`, and `MixtapeEngine` implement queue and mixtape logic.
- `PlaybackMediaIdPolicy`, `PlaybackArtworkResolver`, and `PlaybackSkipPolicy` define session IDs, artwork, and skip behavior.
- `PlaybackControlSync` keeps UI playback speed / seek sizes aligned with Media3 when a session is cleared or a new queue starts, and sanitizes user-requested speeds before apply/persist.
- `HistoryRecommendationLogic`, `AutoVoiceSearchLogic`, `SmartQueueRefillPolicy`, `MixtapeResumePolicy`, `NightWindowLogic`, and `ListeningHistoryUpsertLogic` are JVM-testable playback helpers.
- `AutoArtworkFetchLogic` and `AutoCollageFreshnessLogic` encode Android Auto artwork fetch / collage cache policy for hermetic tests.
- `AutoCollagePrewarmPolicy` and `AutoCollageFolderLogic` encode prewarm throttle and aligned image/key folder inputs for hermetic tests.
- `PlaybackIntroOutroController` manages intro-skip and outro-trim playback lifecycle.
- `service.BoxLorePlaybackService`, `service.MediaDownloadService`, and `service.AutoCollageProvider` are manifest-facing services.
- `service.SmartQueueRefillCoordinator`, `service.CoilBitmapLoader`, and `service.auto.*` support service internals and Android Auto.
- Android Auto browse artwork:
  - `AutoArtworkRepository` + `AutoArtworkSourceStore` register remote/local sources into an in-memory map immediately and `commit` prefs on a background thread before/while returning `content://…/art|local|collage/…` URIs.
  - `AutoCollageProvider` lazily fetches remote covers with validated HTTPS redirects, lenient image content-types, magic-byte checks, and one retry; folder collage URIs include a `v=` cache-buster so Auto hosts reload when resume/history content changes.
  - `AutoCollagePrewarmer` / `AutoCollageGenerator` / `AutoCollageLayouts` rebuild section collages from content keys (resume episode IDs, queue IDs, subscriptions, …), use a shorter TTL for partial/fallback tiles, and refresh on mark-complete / queue changes via `AutoBrowseLibraryHost.requestAutoCollageRefresh`.
  - `AutoArtworkDownloader` is the shared HTTPS fetch path (validated redirects + public-host checks) used by both collage generation and the ContentProvider.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/playback/
  PlaybackRepository.kt              # session core; delegates ListeningHistory* ports
  PlaybackHistoryStore.kt            # history ports only; implements history ports
  PlaybackHistoryStoreApi.kt         # non-port history helpers (extensions)
  PlaybackHistoryMappings.kt         # history entity ↔ model mappers
  PlaybackHistoryDeps.kt             # player + data deps for HistoryStore ctors
  PlaybackSleepController.kt         # sleep timer + late-night nudge
  PlaybackRepositoryQueueApi.kt      # queue extension API
  PlaybackRepositoryTransportApi.kt  # transport / seek / speed extension API
  PlaybackRepositoryHistoryApi.kt    # non-port history extension API
  PlaybackRepositorySleepApi.kt      # sleep / nudge extension API
  PlaybackRepositoryChaptersApi.kt   # chapters / transcript extension API
  QueueManager.kt
  QueueRepository.kt
  ...
  service/
    BoxLorePlaybackService.kt
    MediaDownloadService.kt
    AutoCollageProvider.kt
    SmartQueueRefillCoordinator.kt
    auto/
src/main/java/cx/aswin/boxlore/core/data/service/
  AutoCollageProvider.kt
  BoxLorePlaybackService.kt
  MediaDownloadService.kt
```

`PlaybackRepository` implements `ListeningHistoryPort` / `ListeningHistoryBackupPort` via Kotlin
class delegation to `PlaybackHistoryStore`. Same-package extension files expose the remaining
public one-liner API (`playQueue`, `toggleLike`, `setSleepTimer`, …) so the repository class
stays under detekt LargeClass / TooManyFunctions limits.

Files under `core/data/service` are compatibility stubs for old service class names.

## Dependencies

- Project dependencies: `:core:model`, `:core:network`, `:core:database`, `:core:catalog`, `:core:downloads`, `:core:ranking`, `:core:analytics`, and `:core:prefs`.
- Libraries: Media3 ExoPlayer, Media3 Session, Media3 UI, Coil, Palette, Gson, OkHttp, coroutines, and AndroidX core.
- Reverse-edge rule: catalog and downloads must not depend back on playback. Downloads launch `MediaDownloadService` through the app-installed launcher port.

## Threading / lifecycle

- `PlaybackRepository`, `QueueRepository`, and `QueueManager` are application-scoped through `AppContainer`.
- `BoxLorePlaybackService` is a Media3 `MediaLibraryService` and resolves shared dependencies lazily after application startup.
- Player callbacks run on the main thread; database, artwork, and recommendation work use coroutine scopes and background dispatchers.

## Persistence & identity

- Manifest-facing service class names are system identities.
- Media ID prefixes such as `episode:`, `queue:`, and `learn:` are session and Android Auto contracts.
- SharedPreferences file `boxcast_player` stores playback session flags.
- SharedPreferences file `android_auto_artwork_sources` maps Android Auto artwork content keys to remote HTTPS URLs or sandboxed local paths (identity for Auto collage ContentProvider; do not rename lightly).
- Preference key `device_uuid` is a stable install identifier and must not be logged raw.
- Queue, history, and download rows are persisted by `:core:database` and `:core:downloads`.

## Testing notes

- Unit tests live under `core/playback/src/test`.
- Existing coverage includes skip policy, media ID policy, artwork resolution, control sync (speed/seek preserve on clear), history recommendation filtering, voice search, smart-queue refill policy, mixtape resume policy, night-window logic, listening-history upsert logic, queue math, skip memory, smart queue, playback session mapping, Auto artwork fetch/content-type policy, collage freshness signatures, and Auto artwork source-store durability.
- Service-level tests must install shared dependency holders before exercising service code.

```bash
./gradlew :core:playback:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs playback JVM tests.
- Service and Android Auto behavior are primarily validated by app assembly, emulator/device smoke, and manual checks.
- Dependency guard tracks release runtime dependencies for this module.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:downloads` README](../downloads/README.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:app` README](../../app/README.md)
