# `:app`

## Purpose

Application shell: `BoxLoreApplication`, `MainActivity`, `BoxLoreNavHost`, FCM, surveys, and the **sole composition root** (`AppContainer`). Does not own feature UI or data logic beyond wiring. Installs the shared graph into `SharedAppDependenciesHolder` so workers and Media3 services reuse the same instances.

## Public API

- `BoxLoreApplication.container` — Application-scoped `AppContainer`
- `AppContainer` — implements `SharedAppDependencies`; constructs DB → RSS/ranking peers → `PodcastRepository` → `QueueRepository` → `PlaybackRepository` → `QueueManager` → `SmartDownloadManager`
- Ranking/RSS `getInstance` is allowed **only** inside `AppContainer` lazy vals (single install path). Callers must not recreate those graphs.
- After container creation, `SharedAppDependenciesHolder.instance = container` (required before WorkManager / playback service run)
- `LegacyWorkerFactory` — maps pre-rename WorkManager worker FQCNs for one release
- `MainActivity` — Activity lifecycle, theme, edge-to-edge, PostHog/survey wiring, player overlay (`PlayerSheetScaffold`), OPML import dialog, bottom navigation bar
- `BoxLoreNavHost` — full nav graph (all route composables); receives deps from `BoxLoreApplication.container`

## Composition / nav map (A7)

```
MainActivity.setContent
└── BoxCastTheme
    ├── AlertDialog (lore-queue conflict)          ← activity-scoped mutable state
    ├── InAppAnnouncementDialog                    ← userPrefs.activeAnnouncementStream
    ├── Feature-announcement overlay (full screen) ← PostHog flag "active_feature_announcement"
    ├── BoxWithConstraints
    │   ├── Scaffold → PredictiveBackWrapper
    │   │   └── BoxLoreNavHost                     ← navigation/BoxLoreNavHost.kt
    │   │       Routes owned:
    │   │         onboarding                       ← :feature:onboarding
    │   │         home                             ← :feature:home (HomeRoute)
    │   │         learn / learn/history            ← :feature:explore (LearnScreen)
    │   │         briefing                         ← :feature:briefing (BriefingRoute)
    │   │         settings                         ← :feature:home (SettingsScreen)
    │   │         debug                            ← :feature:home (DebugScreen)
    │   │         explore                          ← :feature:explore (ExploreScreen)
    │   │         library / library/history        ← :feature:library
    │   │         library/liked                    ← :feature:library
    │   │         library/subscriptions            ← :feature:library
    │   │         library/downloads (+ settings)   ← :feature:library
    │   │         library/auto_downloads/settings  ← :feature:library
    │   │         library/downloads/show           ← :feature:library
    │   │         podcast/{podcastId}              ← :feature:info  [deep links: boxlore://, boxcast://, https://aswin.cx/...]
    │   │         episode/{episodeId}/...          ← :feature:info  (full-path route)
    │   │         episode/{episodeId}              ← :feature:info  (simplified deep-link route)
    │   ├── BoxLoreNavigationBar (BottomCenter)    ← :core:designsystem
    │   ├── SleepTimerPopup (TopCenter)            ← :core:designsystem
    │   └── PlayerSheetScaffold (TopStart)         ← :feature:player (v2) — NOT rewritten by A7
    ├── OpmlImportDialog                           ← ui/libraryimport; state owned in Activity
    └── FeedbackSheet                              ← :feature:home components
```

**Deep-link schemes** (preserved, not moved): `boxlore://`, `boxcast://`, `https://aswin.cx/boxlore/share`, `https://aswin.cx/boxcast/share`.

**NavHost helper internals** (in `navigation/BoxLoreNavHost.kt`, `internal` visibility):
- `ExploreTabRoutePattern` — route string for the Explore tab root
- `resolveBottomNavTab` / `resolveBottomNavTabFromBackStack` — maps current route to active tab indicator
- `bottomNavTabRoutePattern` — tab-route string by name (used by the nav bar `onNavigate` handler in MainActivity)
- `NavSettingsState` — data class grouping read-only pref snapshot for the settings route
- `NavOpmlCallbacks` — data class grouping OPML import state callbacks

## Internal structure

```text
src/main/java/cx/aswin/boxlore/
  AppContainer.kt          # composition root + SharedAppDependencies
  BoxLoreApplication.kt    # installs holder; owns single UserPreferencesRepository
  MainActivity.kt          # Activity shell (~1 345 LOC after A7; was ~2 800)
  LegacyWorkerFactory.kt
  navigation/
    BoxLoreNavHost.kt      # full nav graph + route helpers (~1 479 LOC)
  fcm/ surveys/ ui/
```

`applicationId` is `cx.aswin.boxlore` (do not change with package renames).

## Dependencies

- → all `:feature:*`, `:core:data`, `:core:playback`, `:core:designsystem`, `:core:model`, `:core:network`
- Firebase, PostHog, WorkManager, Media3 session client usage via data/playback layers

Forbidden: features must not construct parallel ranking/RSS graphs; use container / holder.

## Threading / lifecycle

- `AppContainer` is Application-scoped (created once in `BoxLoreApplication.onCreate`)
- Repositories/managers are lazy; first touch may hit Room / network on the caller’s dispatcher
- Workers resolve deps via `SharedAppDependenciesHolder.require()` (same process instance)

## Persistence & identity

- `applicationId = cx.aswin.boxlore`
- WorkManager worker FQCNs remain stable (`LegacyWorkerFactory` aliases)
- DataStore `user_preferences`, Room DB filename, and ranking DB are owned by core modules — do not rename here

## Testing notes

- JVM: `src/test` (e.g. FCM payload parser)
- Holder unset behavior is covered in `:core:data` (`SharedAppDependenciesHolderTest`)
- Compose/androidTest and Maestro arrive in later phases

```bash
./gradlew :app:testDebugUnitTest
```

## CI relevance

Unit tests run with the app/module suite in CI; instrumented/emulator jobs exercise features separately.

## See also

- Root [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`docs/TESTING.md`](../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A1)
- [`:core:data` README](../core/data/README.md)
- [`:core:playback` README](../core/playback/README.md)
