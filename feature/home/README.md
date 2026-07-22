# `:feature:home`

## Purpose

Owns Home feed presentation, Settings screens, RSS-add UI, and local debug surfaces for ranking diagnostics. It presents data from injected core dependencies and does not own catalog engines, ranking persistence, playback services, download workers, or Room schemas.

The Home feed's Taste / Because-You-Like / greeting-mission rails are driven end-to-end by `:core:catalog`'s `HomePersonalizationCoordinator.loadSlate` (see [`HomeViewModelSlate.kt`](src/main/java/cx/aswin/boxlore/feature/home/HomeViewModelSlate.kt)); this module no longer runs its own affinity heuristic to choose *what* to show, only how to bridge coordinator/ranking output into UI state and long-press feedback.

## Public API

- `HomeRoute`, `HomeScreen`, `HomeFeed`, `HomeViewModel`, and `HomeViewModelAssembler` for the Home route.
- `settings.SettingsScreen`, `SettingsViewModel`, and `SettingsViewModelAssembler` for Settings.
- `DebugScreen` and `DebugViewModel` for local learner and runtime diagnostics.
- Extracted Home UI pieces such as `LibrarySectionRows`, `LibrarySection`, `RecommendationFeedbackMenu`, and section/card components.
- Pure logic helpers under `logic/` for Home assembly, discovery, hero ordering, selection, playback-state mapping, serial episodes, BYL candidate-picker plumbing, and slate/feedback bridging (`HomeAnchorConfidenceLogic`, `HomeMissionContextLogic`, `HomeFeedbackLogic`).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/home/
  HomeFeed.kt
  HomeFeedRecommendations.kt
  HomeScreen.kt
  HomeViewModel.kt
  HomeViewModelAssembler.kt
  HomeViewModelAdaptive.kt
  HomeViewModelLoadData.kt
  HomeViewModelSlate.kt
  HomeViewModelSelected.kt
  HomeViewModelSerial.kt
  HomeDataModels.kt
  HomeUiModels.kt
  DebugScreen.kt
  DebugScreenContent.kt
  DebugViewModel.kt
  AdaptiveLearnerDebugSection.kt
  components/
    LibrarySection.kt
    LibrarySectionRows.kt
    ForYouSection.kt
    BecauseYouLikeSection.kt
    RecommendationFeedbackMenu.kt
    ...
  logic/
    HomeAnchorConfidenceLogic.kt
    HomeMissionContextLogic.kt
    HomeFeedbackLogic.kt
    ...
  settings/
    SettingsScreen.kt
    SettingsViewModel.kt
    SettingsViewModelAssembler.kt
    components/
    dialogs/
    pages/
```

Main Kotlin files should remain below 1000 lines; extracted Home feed, ViewModel, section-row, and logic files keep UI assembly and behavior testable.

### Home slate loading (`HomeViewModelSlate.kt`)

- `observeHomePersonalization()` combines region, clock daypart, subscriptions, the DataStore manual BYL override, locally-hidden podcast ids, and a `_slateRevision` counter, then calls `HomePersonalizationCoordinator.loadSlate` on every change (debounced via `collectLatest`).
- **BYL anchor**: `resolveAnchorPodcastId` maps `AdaptiveRankingRepository.topShowAffinities` through `HomeAnchorConfidenceLogic` (Bayesian evidence → confidence) into `HomeAnchorSelectionLogic.ShowCandidate`s, then runs `HomeAnchorSelectionLogic.selectAutomatic` with the manual override and current anchor for hysteresis. A deliberate manual pick (`HomeViewModel.setOverriddenRecPodcast`) calls `RankingFeedbackRepository.recordManualAnchorSelected`.
- **Mode-driven titles**: `HomePersonalizationModeLogic.tasteSectionTitle/Subtitle` derive the Taste header from `HomePersonalizationMode`; Because-You-Like is hidden unless the mode is `PERSONALIZED`.
- **Greeting discovery mission**: `resolveDiscoveryMission` bridges the sticky-mission DataStore keys and `ContentDaypart` through `HomeMissionContextLogic` into `HomeDiscoveryMissionLogic.select`, rotating exactly one mission per daypart slot; empty missions are omitted from the rail (`HomeViewModelSlate` clears `_activeMission` when `missionEpisodes` is empty).
- **Feedback**: `onRecommendationFeedback` (long-press menu → `RecommendationFeedbackAction`) always calls `RankingFeedbackRepository.recordAction` with the exact exposure token; "Hide this show" also removes the show from all three in-memory rails immediately via `HomeFeedbackLogic.hideShowEverywhere`, while "More like this" / "Not for me" bump `_slateRevision` to force a coordinator reallocation.
- **Exposure tokens**: every rail episode gets a `RankingFeedbackRepository.recordExposure` call keyed by rail (`taste` / `because_you_like` / `mission`); the returned exposure id is cached in `episodeExposureIds` and threaded into `PlaybackRepository.playQueue`'s `sourceContext` Bundle (`HomeViewModel.buildExposureSourceContext`) for exact-token playback attribution.
- **First-launch personalization reset**: `runFirstLaunchPersonalizationResetIfNeeded` resets ranking (`RankingFeedbackRepository.reset`), clears the coordinator's home-candidates cache, clears `BoxcastPrefs` rec/BYL caches, and clears the manual override — gated by a one-time DataStore flag (`hasCompletedFirstLaunchPersonalizationReset`). It does **not** touch history, subscriptions, queue, or likes.
- **Quality-observability**: every `loadSlate` result carries a `SlateResult.qualityTelemetry` (`HomeSlateQualityTelemetry`, computed in `:core:catalog`); `loadHomeSlate` fires `AnalyticsHelper.trackHomeSlateQualitySnapshot(result.qualityTelemetry)` right after the coordinator call — an **observational-only** aggregate (candidate coverage, duplicate/novelty rate, fallback/cache/latency buckets), never episode identifiers. See [`docs/recommendation-system.md`](../../docs/recommendation-system.md#quality-observability-diagnostics-dashboards-only).

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Media3, Coil, Kotlin serialization, Roborazzi for local visual capture tests, Turbine, and Mockito.
- Reverse-edge rule: feature modules must not depend on other feature modules. ViewModels and assemblers must not directly depend on `BoxLoreDatabase`.
- `HomeViewModelDeps.homePersonalizationCoordinator` is supplied by `:app`'s single `AppContainer` (no Hilt/Koin) and passed through `HomeViewModelAssembler`.

## Threading / lifecycle

- ViewModels are scoped by app navigation or Activity owners.
- Repositories, ports, playback, downloads, prefs, and ranking dependencies are application-scoped instances supplied by app wiring.
- Home surfaces emit glossary analytics through `:core:analytics` (no PostHog direct in the feature).
- UI state is exposed through flows and collected by Compose on the main thread.
- Network and database operations run through injected suspend APIs.

## Persistence & identity

- This module owns no storage files or stable preference keys.
- Settings read and write DataStore and `BoxcastPrefs` through `:core:prefs` APIs.
- RSS IDs, ranking database rows, download cache entries, and playback media IDs are owned by core modules.
- Stable Compose test tags include `home_settings_button`, `settings_add_rss_*`, `settings_downloads_smart`, `settings_downloads_auto`, `settings_reset_analytics_confirm`, and `settings_reset_analytics_cancel`.

## Testing notes

- Unit tests live under `feature/home/src/test`.
- Existing coverage includes Settings ViewModel tests, connectivity dependency coverage, Home listening-history formatting, discovery greeting, and pure Home logic helpers.
- Slate-bridging logic has dedicated tests: `HomeAnchorConfidenceLogicTest` (evidence → Bayesian confidence, hidden/hard-excluded candidate mapping), `HomeMissionContextLogicTest` (daypart id + recent-mission carry-over across slot rollover), and `HomeFeedbackLogicTest` (hide-show removal across all three rails + anchor-clear). Mission *rotation* itself is covered at the source in `:core:catalog`'s `HomePersonalizationLogicTest`.
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
- [`docs/recommendation-system.md`](../../docs/recommendation-system.md)
- [`:app` README](../../app/README.md)
