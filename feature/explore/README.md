# `:feature:explore`

## Purpose

Owns Explore discovery and the Learn tab presentation: browse/search discovery, Learn curiosity cards, Learn history, and local UI state for those surfaces. It does not own recommendation engines, network clients, preference storage, playback services, or other feature screens.

## Public API

- `ExploreScreen` and `ExploreViewModel` for the Explore route.
- `LearnScreen` and `LearnViewModel` for the Learn route (curiosity cards, queue/play actions).
- `LearnHistoryScreen` and `LearnHistoryViewModel` for Learn history.
- `LearnCuriosityHistoryStore` for Learn history persistence through prefs APIs.
- `LearnCuriosityCard` as the feature UI model for curiosity cards.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/explore/
  ExploreScreen.kt
  ExploreViewModel.kt
  LearnCuriosityCard.kt
  LearnCuriosityHistoryStore.kt
  LearnHistoryScreen.kt
  LearnHistoryViewModel.kt
  LearnScreen.kt
  LearnViewModel.kt
  components/
  logic/
```

## Dependencies

- Project dependencies: `:core:designsystem`, `:core:catalog`, `:core:playback`, `:core:model`, `:core:network`, `:core:analytics`, `:core:ranking`, and `:core:prefs`.
- Libraries: Compose, Navigation, Coil, lifecycle runtime/ViewModel Compose, Palette, kotlinx.serialization.json, Turbine and coroutines-test for tests.
- Reverse-edge rule: feature modules must not depend on other feature modules.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
- Catalog, playback, ranking, and prefs access come through injected application-scoped dependencies.
- Analytics: Explore/Learn ViewModels and screens call `:core:analytics` façades for glossary search / Learn exhaustion events (no PostHog direct).
- UI runs on the main thread; search, recommendation, and history work use suspend APIs.

## Persistence & identity

- This module owns no raw storage files.
- Learn history and recommendation caches are accessed through `BoxcastPrefs` in `:core:prefs`.
- Explore For You uses the **same** Home bootstrap recommendations payload: hydrate from `BoxcastPrefs` shared cache, refresh via `PodcastRepository.getHomeBootstrapData`, and write that cache back (taste or popular-in-region fallback). It does not call the standalone empty-seed `getPersonalizedRecommendations` path.
- Network DTOs map to feature UI models before entering UI state.

## Testing notes

- Unit tests live under `feature/explore/src/test`.
- Existing coverage includes Learn pagination, Learn deck logic, Explore browse logic, and shared recommendation cache helpers (`ExploreSharedRecommendationsLogicTest`).
- Prefer fakes for repository and prefs dependencies when expanding ViewModel coverage.

```bash
./gradlew :feature:explore:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Explore JVM tests with the project suite.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:app` README](../../app/README.md)
- [`:core:prefs` README](../../core/prefs/README.md)
