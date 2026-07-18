# `:feature:player`

## Purpose

Player overlay UI (mini + full sheet) and pure layout/control logic. Playback engine, queue, and `PlaybackRepository` live in `:core:playback` — this module is presentation only (not a NavHost destination).

## Public API

- `v2.PlayerSheetScaffold` — mini/full player overlay composed from `:app` `MainActivity`
- `QueueScreen`, control/seek/transcript UI pieces
- `v2.logic.*` — JVM-testable layout/control helpers (**do not rewrite behavior casually**)

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/player/
  PlayerControls.kt / QueueScreen.kt / ChaptersSheet.kt / TranscriptView.kt
  v2/          # sheet, mini/full, theme
  v2/logic/    # pure logic for unit tests
```

## Dependencies

- → `:core:model`, `:core:data`, `:core:network`, `:core:designsystem` (playback types via data/playback re-exports as configured)
- Media3 UI/session client pieces as needed for controls

Forbidden: feature → feature; do not construct a second `PlaybackRepository`.

## Threading / lifecycle

- Overlay lives with Activity composition (not a nav route)
- UI on Main; observe Application-scoped `PlaybackRepository` / session state from the container

## Persistence & identity

None owned. Player session flags (`boxcast_player`) and mediaId prefixes are owned by `:core:playback`.

## Testing notes

- Strong JVM coverage under `src/test/.../v2/logic` (`PlayerControlLogicTest`, `PlayerLayoutLogicTest`, `SeekbarLogicTest`, queue/transcript/chapter helpers, etc.)
- `FormatTimeTest` at feature root
- Compose UI `testTag`s for play/pause/queue/mini-player are optional follow-ups — not required for current CI

```bash
./gradlew :feature:player:testDebugUnitTest
```

## CI relevance

Exercised by `unit-tests.yml`. No dedicated instrumented job for this module.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:playback` README](../../core/playback/README.md)
- [`:app` README](../../app/README.md) — `PlayerSheetScaffold` composition
