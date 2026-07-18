# `:feature:library`

## Purpose

Library hub and sub-routes: subscriptions, liked, downloads, history, show details, smart/auto-download settings. Presentation only; download/playback engines stay in core modules.

## Public API

- `LibraryScreen` / `LibraryViewModel` (constructor-injected repos + scorer)
- `HistoryScreen` / `HistoryViewModel`
- `SubscriptionsScreen`, `LikedEpisodesScreen`, `DownloadedEpisodesScreen`
- `SmartDownloadsSettingsScreen`, `AutoDownloadSettingsScreen`

Routes in `:app`: `library`, `library/history`, `library/liked`, `library/subscriptions`, `library/downloads` (+ settings / show / auto-download settings).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/library/
  LibraryScreen.kt / LibraryViewModel.kt
  HistoryScreen.kt / HistoryViewModel.kt
  SubscriptionsScreen.kt / LikedEpisodesScreen.kt / DownloadedEpisodesScreen.kt
  SmartDownloadsSettingsScreen.kt / AutoDownloadSettingsScreen.kt
  PlayAllFab.kt
```

## Dependencies

- → `:core:model`, `:core:data`, `:core:designsystem` (downloads/playback via container-injected types)

Forbidden: feature → feature; routes must use container instances (no local repo recreation).

## Threading / lifecycle

- ViewModels nav-scoped; `DownloadRepository` / `SmartDownloadManager` / ranking scorer are Application-scoped from holders/container
- UI on Main

## Persistence & identity

None owned. Download cache keys and worker FQCNs are owned by `:core:downloads`.

## Testing notes

- JVM: `HistoryFilterTest`, `SubscriptionSortTest`
- Prefer fakes for fuller Library/History VM coverage when expanded

```bash
./gradlew :feature:library:testDebugUnitTest
```

## CI relevance

Exercised by `unit-tests.yml`. Downloads settings hermetic UI coverage lives under `:feature:home` androidTest (`settings_downloads_*` tags).

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:downloads` README](../../core/downloads/README.md)
- [`:app` README](../../app/README.md) — library route map
