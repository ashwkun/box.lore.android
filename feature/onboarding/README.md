# `:feature:onboarding`

## Purpose

First-run onboarding flows (genre, search, import, AI suggestions). Completes via `:core:prefs` (`BoxcastPrefs` / `UserPreferencesRepository`). Does not own prefs storage or catalog engines.

## Public API

- `OnboardingScreen` / `OnboardingViewModel`
- Supporting screens: `GenreOnboardingScreen`, `SearchOnboardingScreen`, `ImportOnboardingScreen`, `AiOnboardingScreen`, `AiSuggestionsScreen`

Route in `:app`: `onboarding`. Deep-link skip path is owned by `:app`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/onboarding/
  OnboardingScreen.kt / OnboardingViewModel.kt
  OnboardingGenreLimits.kt / OnboardingSearchBackStep.kt
  GenreOnboardingScreen.kt / SearchOnboardingScreen.kt
  ImportOnboardingScreen.kt / AiOnboardingScreen.kt / AiSuggestionsScreen.kt
```

## Dependencies

- → `:core:model`, `:core:catalog` (prefs re-export), `:core:designsystem`

Forbidden: feature → feature; do not read `boxcast_prefs` raw — use `BoxcastPrefs`.

## Threading / lifecycle

- ViewModel nav/Activity-scoped for the first-run flow
- Prefs writes go through Application-scoped `UserPreferencesRepository` / `BoxcastPrefs`

## Persistence & identity

None owned. Onboarding completion flags live in DataStore / `boxcast_prefs` via `:core:prefs` — key names must stay stable.

## Testing notes

- JVM: `OnboardingGenreLimitsTest`, `OnboardingSearchBackStepTest` (pure helpers extracted from VM navigation / chart caps)
- Full `OnboardingViewModel` still needs Application + repositories (deferred)

```bash
./gradlew :feature:onboarding:testDebugUnitTest
```

## CI relevance

Compiled in `unit-tests.yml` with the project suite. No module-specific instrumented job.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:prefs` README](../../core/prefs/README.md)
- [`:app` README](../../app/README.md)
