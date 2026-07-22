# `:feature:home`

## Purpose

Owns Home feed presentation, Settings screens, RSS-add UI, and local debug surfaces for ranking diagnostics. It presents data from injected core dependencies and does not own catalog engines, ranking persistence, playback services, download workers, or Room schemas.

## Public API

- `HomeRoute`, `HomeScreen`, `HomeFeed`, `HomeViewModel`, and `HomeViewModelAssembler` for the Home route.
- The discovery area below the daypart greeting presents three editorial rows from the
  catalog’s existing curated provider endpoint. Provider IDs remain internal; listener copy
  uses concise editorial titles and never exposes backend terminology.
- `settings.SettingsScreen`, `SettingsViewModel`, and `SettingsViewModelAssembler` for Settings.
- `DebugScreen` and `DebugViewModel` for local learner and runtime diagnostics.
- Extracted Home UI pieces such as `LibrarySectionRows`, `LibrarySection`, and section/card components.
- Pure logic helpers under `logic/` for Home assembly, discovery, hero ordering, selection, playback-state mapping, serial episodes, and affinity behavior.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/home/
  HomeFeed.kt
  HomeScreen.kt
  HomeViewModel.kt
  HomeViewModelAssembler.kt
  HomeViewModelBecauseYouLike.kt
  HomeViewModelLoadData.kt
  HomeViewModelSelected.kt
  HomeViewModelSerial.kt
  HomeFeedEditorialRows.kt
  HomeDataModels.kt
  HomeUiModels.kt
  DebugScreen.kt
  DebugScreenContent.kt
  DebugViewModel.kt
  AdaptiveLearnerDebugSection.kt
  components/
    LibrarySection.kt
    LibrarySectionRows.kt
    ...
  logic/
    HomeEditorialRowsLogic.kt
  settings/
    SettingsScreen.kt
    SettingsViewModel.kt
    SettingsViewModelAssembler.kt
    components/
    dialogs/
    pages/
```

Main Kotlin files should remain below 1000 lines; extracted Home feed, ViewModel, section-row, and logic files keep UI assembly and behavior testable.

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Media3, Coil, Kotlin serialization, Roborazzi for local visual capture tests, Turbine, and Mockito.
- Reverse-edge rule: feature modules must not depend on other feature modules. ViewModels and assemblers must not directly depend on `BoxLoreDatabase`.

## Threading / lifecycle

- ViewModels are scoped by app navigation or Activity owners.
- Repositories, ports, playback, downloads, prefs, and ranking dependencies are application-scoped instances supplied by app wiring.
- Home surfaces emit glossary analytics through `:core:analytics` (no PostHog direct in the feature).
- Daypart or region changes cancel the previous editorial-row load before painting the new
  greeting’s results. Editorial rows load independently from personalized recommendations.
- UI state is exposed through flows and collected by Compose on the main thread.
- Network and database operations run through injected suspend APIs.

## Persistence & identity

- This module owns no storage files or stable preference keys.
- Settings read and write DataStore and `BoxcastPrefs` through `:core:prefs` APIs.
- RSS IDs, ranking database rows, download cache entries, and playback media IDs are owned by core modules.
- Stable Compose test tags include `home_settings_button`, `settings_add_rss_*`, `settings_downloads_smart`, `settings_downloads_auto`, `settings_reset_analytics_confirm`, and `settings_reset_analytics_cancel`.

## Testing notes

- Unit tests live under `feature/home/src/test`.
- Existing coverage includes Settings ViewModel tests, Home listening-history formatting,
  discovery greeting, editorial-row selection and de-duplication, and pure Home logic helpers.
- Roborazzi goldens for settings dialogs are verified in merge CI (`:feature:home:verifyRoborazziDebug`).

```bash
./gradlew :feature:home:testDebugUnitTest
./gradlew :feature:home:verifyRoborazziDebug
```

## CI relevance

- `unit-tests.yml` runs Home JVM tests, Roborazzi verify, and includes the module in merged coverage verification.
- `scripts/ci/check-feature-no-boxlore-database.sh` guards direct database usage in feature ViewModels and assemblers.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/screenshots/README.md`](../../docs/screenshots/README.md)
- [`:app` README](../../app/README.md)
