# `:feature:home`

## Purpose

Home feed, Settings hub (incl. Add RSS), and Debug tools. Owns presentation only; dependencies are injected from `:app` `AppContainer`. Does not own catalog/ranking engines or Room schemas.

## Public API

- `HomeRoute` / `HomeViewModel` (+ `HomeViewModelAssembler`)
- `settings.SettingsScreen` / `SettingsViewModel` (RSS add + recommendation reset; `SettingsViewModelAssembler`)
- `DebugScreen` / `DebugViewModel` (learner inspector / ranking debug surfaces)

Routes owned in `:app` nav: `home`, `settings`, `debug`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/home/
  HomeScreen.kt / HomeViewModel.kt / HomeViewModelAssembler.kt
  DiscoveryGreeting.kt / HomeListeningHistoryItem.kt
  DebugScreen.kt / DebugViewModel.kt / AdaptiveLearnerDebugSection.kt
  components/ settings/ settings/pages/ settings/dialogs/ logic/
```

## Dependencies

- → `:core:model`, `:core:data` (re-exports `:core:prefs` / ranking / domain), `:core:designsystem`
- Recommendation / genre caches and learner-log toggle go through `BoxcastPrefs` (no raw `boxcast_prefs`)
- `HomeViewModel` takes `LocalCatalogPort` (not `BoxLoreDatabase`)

Forbidden: feature → feature Gradle edges; no direct `BoxLoreDatabase` in VMs/assemblers.

## Threading / lifecycle

- ViewModels are Activity/Nav back-stack scoped; repos/ports are Application-scoped from the container
- UI state via `StateFlow` / Compose on Main; network/DB via injected suspend APIs

## Persistence & identity

None owned. Uses DataStore / `BoxcastPrefs` / ranking DB through core modules — do not invent parallel prefs files.

## Testing notes

- Assemblers: `HomeViewModelAssembler`, `settings.SettingsViewModelAssembler`
- JVM: `SettingsViewModelTest` (Turbine + fake RSS/ranking ports); `DiscoveryGreetingTest`; `logic/PodcastAffinityLogicTest`; `HomeListeningHistoryItemTest`
- Full Home VM construction still needs Application + heavy fakes (deferred)
- **androidTest:** `AddRssFeedDialogUiTest`; `DownloadsSettingsPageUiTest`; screenshot smoke `AddRssFeedDialogScreenshotStubTest`
- Stable `testTag`s: `home_settings_button`; `settings_add_rss_*`; `settings_downloads_smart` / `settings_downloads_auto`

```bash
./gradlew :feature:home:testDebugUnitTest
./gradlew :feature:home:compileDebugAndroidTestKotlin
./gradlew :feature:home:connectedDebugAndroidTest
```

## CI relevance

- `unit-tests.yml` — JVM + Kover merged (`:koverVerifyMerged`)
- `android-instrumented-tests.yml` — `:feature:home:connectedDebugAndroidTest`
- `scripts/ci/check-feature-no-boxlore-database.sh` guards VMs/assemblers

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/screenshots/README.md`](../../docs/screenshots/README.md)
- [`:app` README](../../app/README.md) — route wiring
