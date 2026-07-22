# `:app`

## Purpose

The application module owns the Android app shell: `BoxLoreApplication`, `MainActivity`, navigation wiring, FCM entry points, in-app announcements, surveys, OPML import UI, and the single production composition root in `AppContainer`. It wires feature screens and core services together; feature UI and data engines remain in their owning modules.

## Public API

- `BoxLoreApplication.container` exposes the process-scoped `AppContainer`.
- On startup, `BoxLoreApplication` configures `LearningEventLog` via `BoxcastPrefs.resolveLearnerLogEnabled`: on by default in debug when unset; **always off in release** unless the user has explicitly persisted an opt-in from the debug screen.
- On startup, `BoxLoreApplication` also fires two fire-and-forget, once-per-launch diagnostics: `reportAdaptiveRankingStatus` (`adaptive_ranking_status` — learning stage per objective) and `reportHomeLearningAttributionHealth` (`home_learning_attribution_health` — HOME exact-exposure-token attribution plumbing health). Both are optional and swallow failures so they never destabilize startup; see [`docs/recommendation-system.md`](../docs/recommendation-system.md#quality-observability-diagnostics-dashboards-only).
- `AppContainer` constructs the shared graph: database, network, RSS, ranking, catalog, playback, queue, downloads, prefs, and analytics dependencies.
- Install attribution: `AppContainer` wires `InstallReferrerManager.onInstallReferrerResolved` → analytics person properties (`install_channel`). Catalog stays free of `:core:analytics`.
- FCM (`BoxLoreFcmService`) owns notification_received / tap extras (`notification_type`, podcast/episode ids for snake+camel keys). Generic push intents propagate those extras so taps are not always `"push"`.
- Library backup/import analytics (`trackBackupRestoreResult`, import failed) use allowlisted error codes from `LibraryBackupAnalyticsErrors` — never raw exception text.
- `MainActivity` / `BoxLoreAppRoot` own deep-link and session-restore analytics at the shell layer.
- `SharedAppDependenciesHolder` and `DownloadsDependenciesHolder` are installed from the application so workers and Media3 services reuse the same graph.
- `DownloadServiceLauncherHolder` is installed with `MediaDownloadService::class.java` so `:core:downloads` can launch the foreground download service without depending on `:core:playback`.
- `LegacyWorkerFactory` maps legacy worker class names to current worker implementations for WorkManager continuity.
- `MainActivity` hosts theme, edge-to-edge setup, app update hooks, surveys, OPML import state, bottom navigation, and the player overlay.
- `BoxLoreNavHost` owns app route registration and delegates screen bodies to feature modules.
- Tab destinations in `NavGraphTabDestinations` wire Home / Explore / Learn / Library / player entry points to feature screens.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/
  AppContainer.kt
  BoxLoreApplication.kt
  MainActivity.kt
  LegacyWorkerFactory.kt
  navigation/
    BoxLoreNavHost.kt
    BottomNavNavigation.kt
    NavGraphWiring.kt
    NavGraphTabDestinations.kt
    NavGraphLibrarySettingsDestinations.kt
    NavGraphPodcastEpisodeDestinations.kt
    PushTargetRouteAllowlist.kt
  connectivity/
  fcm/
  lifecycle/
  surveys/
  ui/
    announcement/
    libraryimport/
      OpmlImportDialog.kt
      OpmlImportEffects.kt
      OpmlImportProgressContent.kt
  updates/
  util/
```

Routes include onboarding, home, learn, briefing, settings, debug, explore, library sub-routes, podcast details, and episode details. Deep-link schemes remain `boxlore://`, `boxcast://`, `https://aswin.cx/boxlore/share`, and `https://aswin.cx/boxcast/share`.

## Dependencies

- Project dependencies: all `:feature:*` modules plus `:core:analytics`, `:core:catalog`, `:core:designsystem`, `:core:domain`, `:core:downloads`, `:core:model`, `:core:network`, `:core:playback`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, Firebase, PostHog, WorkManager, Media3 client usage, Coil, Retrofit, Kotlin serialization, and Play Core.
- Reverse-edge rule: feature modules and core modules must not construct independent application graphs; they receive instances from `AppContainer` or the dependency holders.

## Threading / lifecycle

- `AppContainer` is created once from `BoxLoreApplication.onCreate` and is application-scoped.
- Workers resolve dependencies through installed holders before doing background work.
- Media3 services lazily resolve the shared graph after application startup.
- UI composition, navigation, OPML import state, surveys, and player overlay state are Activity-scoped.

## Persistence & identity

- `applicationId` is `cx.aswin.boxlore`.
- Manifest service, receiver, activity, and worker class names are system-facing identities.
- `LegacyWorkerFactory` preserves WorkManager upgrades from legacy worker class names.
- Build config reads `BOXLORE_API_BASE_URL` and `BOXLORE_PUBLIC_KEY`, with legacy local-property names still accepted for existing developer environments.
- Core modules own Room filenames, DataStore names, SharedPreferences names, ranking identity, RSS IDs, and playback media IDs.

## Testing notes

- Unit tests live under `app/src/test`, including app container smoke coverage, worker factory mapping, FCM payload parsing (type + snake/camel ids), library backup analytics error codes, and push-target route allowlisting.
- Navigation and feature UI behavior are covered mainly in feature module tests and Maestro smoke flows.

```bash
./gradlew :app:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs app JVM tests and uses the CI Firebase configuration stub.
- `maestro-nightly.yml` exercises installed-app smoke flows when the optional device-farm secrets are available.
- App assembly validates dependency wiring for all feature and core modules.

## See also

- [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`docs/TESTING.md`](../docs/TESTING.md)
- [`:core:catalog` README](../core/catalog/README.md)
- [`:core:downloads` README](../core/downloads/README.md)
- [`:core:playback` README](../core/playback/README.md)
