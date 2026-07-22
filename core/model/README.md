# `:core:model`

## Purpose

Owns shared domain models, enums, and pure value helpers used across network, data, playback, and UI modules. It does not own network DTOs, Room entities, Compose UI, repositories, or Android lifecycle behavior.

## Public API

- Podcast, episode, briefing, chapter, person, transcript, and playback-adjacent model types.
- `PlaybackEntryPoint`, `ShareTarget`, and `ShareLinkBuilder`.
- `AutoTranscriptState`.
- `PodcastGenres` and `RankingAggregateTelemetry`.
- `HomeSlateQualityTelemetry` and `RankingExposureHealthTelemetry` — bucketed, privacy-safe quality-observability aggregates shared between `:core:catalog` / `:core:ranking` (compute) and `:core:analytics` (emit); see [`docs/recommendation-system.md`](../../docs/recommendation-system.md#quality-observability-diagnostics-dashboards-only).
- Cross-promotion model types.
- `SleepTimerConstants`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/model/
  Briefing.kt
  Chapter.kt
  CrossPromotion.kt
  Episode.kt
  Podcast.kt
  PodcastGenres.kt
  RankingAggregateTelemetry.kt
  HomeSlateQualityTelemetry.kt
  RankingExposureHealthTelemetry.kt
  ShareLinkBuilder.kt
  SleepTimerConstants.kt
  Transcript.kt
  ...
```

## Dependencies

- Project dependencies: none.
- Libraries: Kotlinx serialization.
- Reverse-edge rule: Android framework, Room, Retrofit implementation details, repositories, and Compose UI must stay out of this module.

## Threading / lifecycle

- Types are immutable or treated as immutable value data.
- No lifecycle owners, singletons, dispatchers, or background work are created here.

## Persistence & identity

- This module owns no persistence files or storage keys.
- ID schemes carried by model fields are owned by their source modules, such as RSS podcast IDs and playback media IDs.

## Testing notes

- Unit tests live under `core/model/src/test`.
- `ShareLinkBuilderTest` covers share URL invariants.
- Prefer pure JVM tests for additional formatters or value helpers.

```bash
./gradlew :core:model:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` compiles this module for nearly every test target.
- No module-specific CI job is required beyond JVM tests.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:network` README](../network/README.md)
- [`:core:database` README](../database/README.md)
