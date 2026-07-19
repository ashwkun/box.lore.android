# `:feature:library`

## Purpose

Owns Library presentation: hub, history, subscriptions, liked episodes, downloaded episodes, show details, smart-download settings, and auto-download settings. It does not own download workers, playback services, ranking storage, catalog persistence, or app route registration.

## Public API

- `LibraryScreen` and `LibraryViewModel`.
- `HistoryScreen` and `HistoryViewModel`.
- `SubscriptionsScreen`, `LikedEpisodesScreen`, and `DownloadedEpisodesScreen`.
- `SmartDownloadsSettingsScreen` and `AutoDownloadSettingsScreen`.
- `PlayAllFab` and library UI helpers.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/library/
  AutoDownloadSettingsScreen.kt
  DownloadedEpisodesScreen.kt
  HistoryScreen.kt
  HistoryViewModel.kt
  LibraryScreen.kt
  LibraryViewModel.kt
  LikedEpisodesScreen.kt
  PlayAllFab.kt
  SmartDownloadsSettingsScreen.kt
  SubscriptionsScreen.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:designsystem`, `:core:analytics`, and `:core:ranking`.
- Libraries: Compose, Navigation, Activity Compose, lifecycle ViewModel/runtime, Coil, Material adaptive, Turbine / JUnit for JVM tests.
- Reverse-edge rule: feature modules must not depend on other feature modules or create local repository graphs.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
- Download, playback, catalog, and ranking dependencies are application-scoped instances supplied by app wiring.
- UI runs on the main thread; history, download, and subscription operations use injected suspend APIs.

## Persistence & identity

- This module owns no storage files or stable keys.
- Download cache entries and worker identities are owned by `:core:downloads`.
- Playback media IDs are owned by `:core:playback`.
- Catalog and subscription identities are owned by catalog, RSS, and database modules.

## Testing notes

- Unit tests live under `feature/library/src/test`.
- `HistoryFilterTest` covers history filtering behavior.
- `SubscriptionSortTest` covers subscription ordering.
- Broader ViewModel coverage should use fakes for catalog, download, playback, and ranking dependencies.

```bash
./gradlew :feature:library:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Library JVM tests with the project suite.
- Downloads settings visual coverage is in `:feature:home` Roborazzi goldens.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:downloads` README](../../core/downloads/README.md)
- [`:app` README](../../app/README.md)
