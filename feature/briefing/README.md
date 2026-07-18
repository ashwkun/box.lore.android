# `:feature:briefing`

## Purpose

Daily briefing playback UI and ViewModel (region selection, chapters/transcript wiring via podcast repository). Presentation only; playback/queue engines stay in `:core:playback`.

## Public API

- `BriefingScreen` / `BriefingViewModel` (injected `PodcastRepository`, `PlaybackRepository`, `QueueManager`)
- `BriefingUiState`

Route in `:app`: `briefing`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/briefing/
  BriefingScreen.kt
  BriefingViewModel.kt
  BriefingUiState.kt
```

## Dependencies

- → `:core:model`, `:core:data`, `:core:designsystem` (playback/queue via container-injected types)

Forbidden: feature → feature; do not construct a parallel playback graph.

## Threading / lifecycle

- ViewModel nav-scoped; `PlaybackRepository` / `QueueManager` Application-scoped from `AppContainer`
- UI on Main

## Persistence & identity

None owned. Briefing content identity comes from catalog/network models; playback session identity from `:core:playback`.

## Testing notes

- No dedicated `src/test` suite currently
- Manual smoke: open briefing from home; play/resume via shared playback graph

```bash
./gradlew :feature:briefing:testDebugUnitTest
```

## CI relevance

Compiled in `unit-tests.yml`. No module-specific instrumented job.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:playback` README](../../core/playback/README.md)
- [`:app` README](../../app/README.md)
