# `:app`

## Purpose

Application shell: `BoxLoreApplication`, `MainActivity` NavHost, FCM, surveys, and the **sole composition root** (`AppContainer`). Does not own feature UI or data logic beyond wiring. Installs the shared graph into `SharedAppDependenciesHolder` so workers and Media3 services reuse the same instances.

## Public API

- `BoxLoreApplication.container` — Application-scoped `AppContainer`
- `AppContainer` — implements `SharedAppDependencies`; constructs DB → RSS/ranking peers → `PodcastRepository` → `QueueRepository` → `PlaybackRepository` → `QueueManager` → `SmartDownloadManager`
- Ranking/RSS `getInstance` is allowed **only** inside `AppContainer` lazy vals (single install path). Callers must not recreate those graphs.
- After container creation, `SharedAppDependenciesHolder.instance = container` (required before WorkManager / playback service run)
- `LegacyWorkerFactory` — maps pre-rename WorkManager worker FQCNs for one release
- `MainActivity` — nav graph, player overlay (`PlayerSheetScaffold`), deep links

## Internal structure

```text
src/main/java/cx/aswin/boxlore/
  AppContainer.kt          # composition root + SharedAppDependencies
  BoxLoreApplication.kt    # installs holder
  MainActivity.kt
  LegacyWorkerFactory.kt
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
