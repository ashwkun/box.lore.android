# `:core:analytics`

## Purpose

Owns all analytics event capture for Boxlore. Wraps the PostHog Android SDK behind a thin
façade so call-sites never touch the SDK directly. Provides `AnalyticsHelper` (the production
singleton) and `RecordingAnalytics` (a test-double that records events in-memory with no PostHog
dependency).

**Does not own:** PostHog SDK initialisation (stays in `:app`), UI, repositories, or any
persistence beyond the `boxcast_analytics_prefs` SharedPreferences key used for first-launch
detection.

## Public API

| Type | Role |
| :--- | :--- |
| `Analytics` | Interface – key event-capture methods; new call-sites should depend on this |
| `AnalyticsHelper` | Production `object` implementing `Analytics` via PostHog |
| `RecordingAnalytics` | Test-double `class` implementing `Analytics`; records events in-memory |
| `PendingEntryPoint` | Thread-safe singleton bridging playback entry-point across the MediaController IPC boundary |
| `PlayerSessionAggregator` | Aggregates per-episode player interactions, flushed at session end |

Package kept at `cx.aswin.boxlore.core.data.analytics` (no import changes in consumers).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/analytics/
  Analytics.kt              # façade interface
  AnalyticsHelper.kt        # PostHog-backed singleton (implements Analytics)
  RecordingAnalytics.kt     # in-memory test double (implements Analytics)
  PendingEntryPoint.kt      # IPC bridge for playback entry-point context
  SessionAggregator.kt      # PlayerSessionAggregator – batches player-screen events
```

## Dependencies

- → `:core:model` (`RankingAggregateTelemetry`, model types)
- → `libs.posthog.android` (PostHog SDK)
- → `libs.androidx.core.ktx`

**Forbidden reverse edges:** analytics must not depend on `:core:data`, `:core:database`,
`:core:network`, `:core:playback`, `:core:downloads`, or any `feature:*` module.

`:core:data` re-exports `:core:analytics` via `api(projects.core.analytics)` so existing
consumers get the types transitively without any import changes.

## Threading / lifecycle

- All `AnalyticsHelper` methods are callable from any thread; PostHog handles its own
  internal dispatch.
- `PlayerSessionAggregator` and `PendingEntryPoint` use `@Synchronized` / `@Volatile`
  for thread safety; both are plain `object` singletons (Application-scoped).
- `flush()` is a best-effort no-op in `RecordingAnalytics`.

## Persistence & identity

| Stable key | Reason |
| :--- | :--- |
| `boxcast_analytics_prefs` SharedPreferences name | First-launch flag persisted on device |
| `is_first_launch` SharedPreferences key | First-launch flag key |

Do **not** rename these; changing them resets first-launch state for existing users.

## Testing notes

- Pure unit tests live in `src/test/java/cx/aswin/boxlore/core/analytics/`.
- Use `RecordingAnalytics` as the test double for any class that takes `Analytics`.
- `DeriveGenrePersonaTest` exercises `AnalyticsHelper.deriveGenrePersona` without PostHog.
- No Robolectric or Android instrumentation required for these tests.

```bash
./gradlew :core:analytics:testDebugUnitTest
```

## CI relevance

Covered by `unit-tests.yml` (`testDebugUnitTest`). Kover variant `merged` is
registered and included in root `:koverVerifyMerged` (with data/domain/home/rss/downloads).

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md)
- `:core:data` README – explains the `api` re-export and what moved
