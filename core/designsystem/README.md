# `:core:designsystem`

## Purpose

Shared Compose theme, shapes, motion/typography helpers, loaders, image helpers, navigation chrome constants, and share-card UI (`ShareManager`). No feature navigation, repositories, network, or Room.

## Public API

- Theme: `BoxLoreTheme` / expressive theme pieces (`ExpressiveShapes`, `ExpressiveMotion`, typography, dynamic color helpers)
- Components: `OptimizedImage`, `BoxLoreLoader`, `AdvancedPlayerControls`, bottom-nav height / sleep-timer chrome as used by `:app`
- `share.ShareManager` — composite share cards / system share sheet

Callers must not recreate a parallel theme stack inside features.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/designsystem/
  components/ theme/ share/ component/
src/main/res/ drawable/ font/
```

## Dependencies

- → `:core:model`, `:core:analytics` (proxy-fallback tracking via façade)
- Compose Material3, Coil (api), coroutines

Forbidden: designsystem ↛ `:core:catalog`, `:core:network`, `:core:database`, or any `:feature:*`. Architecture guards enforce `:core:catalog` ↛ designsystem. Do not import PostHog — use `AnalyticsHelper`.

## Threading / lifecycle

- Compose UI on Main
- Image loading via Coil (background); no Application-scoped repositories here
- Theme is applied from `:app` / feature composition roots

## Persistence & identity

None. Visual resources (fonts/drawables) may change; no user-data keys or DB filenames.

## Testing notes

- JVM: `ThemeBrandTokensTest` (`BrandSeeds`, custom hex detection, contrast helpers)
- Screenshot / Compose UI tests remain at app or feature level

```bash
./gradlew :core:designsystem:testDebugUnitTest
```

## CI relevance

Compiled as a dependency of app/features in `unit-tests.yml` / instrumented jobs. No module-specific CI job.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md)
- [`:app` README](../../app/README.md) — theme + nav chrome composition
