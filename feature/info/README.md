# `:feature:info`

## Purpose

Podcast and episode detail screens (subscribe, RSS refresh, related/similar, cross-promo). Owns presentation; catalog/offline data comes from injected ports/repos. Dual episode routes are wired from `:app`.

## Public API

- `PodcastInfoScreen` / `PodcastInfoViewModel`
- `EpisodeInfoScreen` / `EpisodeInfoViewModel`
- `InfoViewModelAssembler` — podcast + episode factories
- Supporting cards/components (`CrossPromotionCard`, etc.)

ViewModels take a **shared** `PodcastRepository` plus `LocalCatalogPort` / `EpisodeOfflineLookupPort` (no `BoxLoreDatabase`). Resume progress uses `InfoListeningProgressItem` (Room entities only at the mapper).

Routes in `:app`: `podcast/{podcastId}`, `episode/{episodeId}/…`, `episode/{episodeId}`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/info/
  PodcastInfoScreen.kt / EpisodeInfoScreen.kt
  *ViewModel.kt / InfoViewModelAssembler.kt
  InfoListeningProgressItem.kt
  components/ logic/
```

## Dependencies

- → `:core:model`, `:core:domain`, `:core:data`, `:core:designsystem`

Forbidden: feature → feature; no `BoxLoreDatabase` in VMs/assemblers.

## Threading / lifecycle

- ViewModels nav-scoped; repositories/ports Application-scoped from `AppContainer`
- UI on Main; refresh/subscribe via suspend APIs

## Persistence & identity

None owned. Respects `rss:` IDs and catalog identity from `:core:rss` / `:core:database`.

## Testing notes

- JVM: `InfoViewModelAssemblerTest`, `InfoCatalogPortBehaviorTest`, `InfoCatalogPortErrorBehaviorTest`, `logic/EpisodeOfflineMergeLogicTest`, `InfoListeningProgressItemTest`, `EpisodeDurationFormatterTest`
- Full Info VMs still need Application — prefer port/fake tests
- Catalog HTTP paths covered in `:core:data` `PodcastRepositoryCatalogTest`

```bash
./gradlew :feature:info:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` — JVM suite
- `scripts/ci/check-feature-no-boxlore-database.sh` guards VMs/assemblers

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:data` README](../../core/data/README.md) — catalog MockWebServer tests
- [`:app` README](../../app/README.md) — deep-link routes
