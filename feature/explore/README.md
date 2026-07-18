# `:feature:explore`

## Purpose

Explore discovery and **Learn** bottom-nav tab (curiosity card stack + Learn history). Presentation only; not a Gradle dependency of other features.

## Public API

- `ExploreScreen` / `ExploreViewModel`
- `LearnScreen` / `LearnViewModel`
- `LearnHistoryScreen` / `LearnHistoryViewModel`
- `LearnCuriosityHistoryStore`
- `LearnCuriosityCard` — feature UI model for Learn cards (not network DTOs)

Routes in `:app`: `explore`, `learn`, `learn/history`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/explore/
  ExploreScreen.kt / LearnScreen.kt / LearnHistoryScreen.kt
  *ViewModel.kt / LearnCuriosityHistoryStore.kt / LearnCuriosityCard.kt
  CuriosityCardStack.kt / components/ logic/
```

## Dependencies

- → `:core:model`, `:core:data` (re-exports `:core:prefs`), `:core:designsystem`, `:core:network` as needed
- `LearnCuriosityHistoryStore` and Explore recommendation reads use `BoxcastPrefs` (no raw `boxcast_prefs`)
- Network `DailyCuriosityDto` maps to `LearnCuriosityCard` at the boundary — UI state must not hold DTOs

Forbidden: feature → feature.

## Threading / lifecycle

- ViewModels nav-scoped; prefs/repos Application-scoped from the container
- UI on Main

## Persistence & identity

None owned. Learn curiosity history keys live in `BoxcastPrefs` / `:core:prefs` — do not open `boxcast_prefs` raw.

## Testing notes

- JVM: `LearnPaginationTest`, `logic/LearnDeckLogicTest`, `logic/ExploreBrowseLogicTest`
- UI/Maestro Learn-tab coverage is optional follow-up (see `maestro/`)

```bash
./gradlew :feature:explore:testDebugUnitTest
```

## CI relevance

Exercised by `unit-tests.yml`. No dedicated instrumented job.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:app` README](../../app/README.md) — Explore/Learn route ownership
- [`:core:prefs` README](../../core/prefs/README.md)
