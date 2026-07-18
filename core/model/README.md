# `:core:model`

## Purpose

Pure domain models and enums shared across network, data, and UI. No Android framework dependencies beyond what the module already declares for serialization. Does **not** own network DTOs (`:core:network`), Room entities (`:core:database`), or Compose.

## Public API

- Podcast / Episode / Briefing / Chapter-related models
- `PlaybackEntryPoint`, `ShareTarget`, `ShareLinkBuilder`
- `AutoTranscriptState` (playback + player UI)
- `PodcastGenres`, `RankingAggregateTelemetry`
- Cross-promotion model types (`CrossPromotion`)
- `SleepTimerConstants`

Prefer these types at feature/UI boundaries; map network DTOs at repository edges.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/model/
  Podcast.kt / Episode.kt / Briefing.kt / …
```

## Dependencies

- Kotlinx Serialization only (no project deps)

Forbidden reverse edges: nothing should push Android/Room/Retrofit into this module.

## Threading / lifecycle

- Immutable / value types; no lifecycle ownership
- Safe to share across threads when treated as immutable data

## Persistence & identity

None owned here. ID *schemes* (`rss:`, mediaId prefixes) are documented in owning modules (`:core:rss`, `:core:playback`); model types may carry those string IDs as opaque values.

## Testing notes

- JVM: `ShareLinkBuilderTest` (share URL invariants)
- Prefer pure JVM unit tests for formatters/builders when added

```bash
./gradlew :core:model:testDebugUnitTest
```

## CI relevance

Compiled as a dependency of nearly every module in `unit-tests.yml`. No module-specific job.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:network` README](../network/README.md) — DTOs that map into these models
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md)
