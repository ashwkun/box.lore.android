# `:core:playback`

## Purpose

Owns playback session control, queue orchestration, and Media3 services (player + offline download foreground service + Android Auto collage provider). Deliberately does **not** own Room schemas, prefs DataStore, ranking, RSS, or smart-download workers (those stay in `:core:data` / `:core:database`).

## Public API

Stable types/entry points other modules may depend on:

- `PlaybackRepository` — one UI/session instance; ctor-injected `RankingFeedbackRepository` (never call ranking `getInstance` here)
- `QueueRepository` / `QueueManager` — queue persistence + explicit play/add orchestration
- `playback.PlaybackSkipPolicy` — intro/outro trim and seek policy
- Services (FQCN must stay stable across releases):
  - `cx.aswin.boxlore.core.data.service.BoxLorePlaybackService` — uses `SharedAppDependenciesHolder.require()` for podcast/ranking/download/prefs (no parallel graph)
  - `cx.aswin.boxlore.core.data.service.MediaDownloadService`
  - `cx.aswin.boxlore.core.data.service.AutoCollageProvider`

Packages remain under `cx.aswin.boxlore.core.data.*` for AndroidManifest / WorkManager / MediaSession stability.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  PlaybackRepository.kt
  QueueManager.kt
  QueueRepository.kt
  playback/     # PlaybackSkipPolicy
  service/      # BoxLorePlaybackService, MediaDownloadService, AutoCollage*
  service/auto/ # Android Auto browse helpers
```

Smart Queue engine / skip memory / queue math stay in `:core:data` (service depends on them; scorer comes from the shared holder).

## Dependencies

Gradle edges (project + notable libs):

- → `:core:data` (api), `:core:database`, `:core:network`, `:core:model`
- Media3 exoplayer / session / ui
- Coil (session artwork), coroutines (+ guava await)

Forbidden reverse edge: `:core:data` ↛ `:core:playback`. Backup/history seams use `ports.ListeningHistoryBackupPort`; download starts use the MediaDownloadService FQCN string from data.

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

## Testing notes

- JVM unit tests under `src/test` (e.g. `PlaybackSkipPolicyTest`)
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
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A1)
- [`:core:data` README](../data/README.md)
- [`:app` README](../../app/README.md)
