# `:core:domain`

## Purpose

Owns thin domain ports (interfaces) and small result types that feature ViewModels and workers depend on for fakes/tests — without pulling in full repositories. Deliberately does **not** own Room entities, Retrofit clients, ranking engines, Compose, or production implementations.

## Public API

Stable ports/results other modules may depend on:

- `ports.RssSubscriptionPort` / `RssSubscriptionResult`
- `ports.RankingResetPort`
- `ports.PodcastCatalogPort`
- `ports.HistoryRecommendationSource` (uses network `HistoryItem`)
- `ports.LocalCatalogPort` — local podcast get / linked RSS / subscribed upsert (domain `Podcast` only)
- `ports.EpisodeOfflineLookupPort` + `OfflineEpisodeSnapshot` — download/history hydration for Episode Info

**Must not live here:** Room entities, Retrofit DTOs beyond tiny shared network types already on the edge, Compose, managers, or WorkManager workers.

`ListeningHistoryBackupPort` stays in `:core:data` (uses Room `ListeningHistoryEntity` to avoid a domain → database edge).

Production impls: `RoomLocalCatalog` / `RoomEpisodeOfflineLookup` in `:core:data`; RSS port in `:core:rss`; ranking reset in `:core:ranking` — wired from `AppContainer`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/domain/
  RssSubscriptionResult.kt
  ports/
    RssSubscriptionPort.kt
    RankingResetPort.kt
    PodcastCatalogPort.kt
    HistoryRecommendationSource.kt
    LocalCatalogPort.kt
    EpisodeOfflineLookupPort.kt
```

## Dependencies

- → `:core:model` (api)
- → `:core:network` (api; `HistoryItem` only)
- kotlinx-coroutines

Forbidden: domain ↛ `:core:data`, `:core:database`, `:core:playback`, features, or designsystem.

## Threading / lifecycle

- Ports are suspend / Flow contracts; callers choose dispatchers
- No Application-scoped objects in this module — inject implementations from `AppContainer`

## Persistence & identity

None owned here. Ports may mention `rss:` IDs or episode IDs in docs/contracts; persistence ownership stays in `:core:rss` / `:core:database`.

## Testing notes

JVM under `src/test`:

- `RssSubscriptionResultTest`
- `ports/LocalCatalogPortTest`, `EpisodeOfflineLookupPortTest`, `HistoryRecommendationSourceTest`, `RankingResetPortTest`

Prefer constructor injection + fakes over full repository graphs. Shared builders: `:core:testing` `TestFixtures`.

```bash
./gradlew :core:domain:testDebugUnitTest
```

## CI relevance

Exercised by `unit-tests.yml`. Included in Kover merged coverage gate.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:data` README](../data/README.md) — production implementations
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md)
