# `:core:playback`

## Purpose

Owns playback session control, queue orchestration, and Media3 services (player + offline download foreground service + Android Auto collage provider). Deliberately does **not** own Room schemas, prefs DataStore, ranking, RSS, or smart-download workers (those stay in `:core:data` / `:core:database`).

## Public API

Stable types/entry points other modules may depend on:

- `PlaybackRepository` — one UI/session instance; never recreate per route or worker
- `QueueRepository` / `QueueManager` — queue persistence + explicit play/add orchestration
- `playback.PlaybackSkipPolicy` — intro/outro trim and seek policy
- Services (FQCN must stay stable across releases):
  - `cx.aswin.boxlore.core.data.service.BoxLorePlaybackService`
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

Smart Queue engine / skip memory / queue math stay in `:core:data` (service depends on them).

## Dependencies

Gradle edges (project + notable libs):

- → `:core:data` (api), `:core:database`, `:core:network`, `:core:model`
- Media3 exoplayer / session / ui
- Coil (session artwork), coroutines (+ guava await)

Forbidden reverse edge: `:core:data` ↛ `:core:playback`. Backup/history seams use `ports.ListeningHistoryBackupPort`; download starts use the MediaDownloadService FQCN string from data.

## Testing notes

- JVM unit tests under `src/test` (e.g. `PlaybackSkipPolicyTest`)
- Prefer fakes from `:core:testing` for broader playback graph tests in later phases

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md) for cross-module rules
