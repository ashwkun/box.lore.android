# `:core:analytics`

## Purpose

Owns all analytics event capture for Boxlore. Wraps the PostHog Android SDK behind a thin
façade so call-sites never touch the SDK directly. Provides `AnalyticsHelper` (the production
singleton) and `RecordingAnalytics` (a test-double that records events in-memory with no PostHog
dependency). Also owns `ErrorReporter` (non-fatal error sink; `:app` may install Crashlytics).

**Does not own:** PostHog SDK initialisation (stays in `:app`), UI, repositories, or any
persistence beyond the `boxlore_analytics_prefs` SharedPreferences file used for first-launch
detection (migrated from `boxcast_analytics_prefs` via `PrefsFileMigrator`).

## Public API

| Type | Role |
| :--- | :--- |
| `Analytics` | Interface – key event-capture methods; new call-sites should depend on this |
| `AnalyticsHelper` | Production `object` implementing `Analytics` via PostHog |
| `RecordingAnalytics` | Test-double `class` implementing `Analytics`; records events in-memory |
| `ErrorReporter` | Non-fatal error façade (Logcat default; Crashlytics installed from `:app`) |
| `PendingEntryPoint` | Thread-safe singleton bridging playback entry-point across the MediaController IPC boundary |
| `PlayerSessionAggregator` | Aggregates per-episode player interactions, flushed at session end |

**Package root:** `cx.aswin.boxlore.core.analytics` (matches module).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/analytics/
  Analytics.kt              # façade interface
  AnalyticsHelper.kt        # PostHog-backed singleton (implements Analytics)
  RecordingAnalytics.kt     # in-memory test double (implements Analytics)
  ErrorReporter.kt          # non-fatal error sink
  PendingEntryPoint.kt      # IPC bridge for playback entry-point context
  SessionAggregator.kt      # PlayerSessionAggregator – batches player-screen events
  *AnalyticsTracks.kt       # PostHog capture helpers grouped by domain
```

## Dependencies

- → `:core:model` (`RankingAggregateTelemetry`, model types)
- → `:core:prefs` (`PrefsFileMigrator` for analytics prefs file)
- → `libs.posthog.android` (PostHog SDK)
- → `libs.androidx.core.ktx`

**Forbidden reverse edges:** analytics must not depend on `:core:catalog`, `:core:database`,
`:core:network`, `:core:playback`, `:core:downloads`, or any `feature:*` module.

`:core:catalog` does **not** re-export analytics. Features / designsystem / playback that
emit events must declare `implementation(projects.core.analytics)` directly. CI guard:
`scripts/ci/check-feature-no-posthog.sh`.

## Threading / lifecycle

- All `AnalyticsHelper` methods are callable from any thread; PostHog handles its own
  internal dispatch.
- `PlayerSessionAggregator` and `PendingEntryPoint` use `@Synchronized` / `@Volatile`
  for thread safety; both are plain `object` singletons (Application-scoped).
- `flush()` is a best-effort no-op in `RecordingAnalytics`.

## Persistence & identity

| Stable key | Reason |
| :--- | :--- |
| `boxlore_analytics_prefs` SharedPreferences name | First-launch flag (migrated from `boxcast_analytics_prefs`) |
| `is_first_launch` SharedPreferences key | First-launch flag key |

Do **not** rename the key; file identity migrates only via `PrefsFileMigrator`.

## Testing notes

- Pure unit tests live in `src/test/java/cx/aswin/boxlore/core/analytics/`.
- Use `RecordingAnalytics` as the test double for any class that takes `Analytics`.
- `DeriveGenrePersonaTest` exercises `AnalyticsHelper.deriveGenrePersona` without PostHog.
- `AnalyticsGlossaryAllowlistTest` + `AnalyticsRawTextAndEntryPointTest` assert Phase A∪B allowlist,
  entry_point normalization, and raw `user_input_text` / `search_query` attachment via `AnalyticsEmit` sink.
- No Robolectric or Android instrumentation required for these tests.

```bash
./gradlew :core:analytics:testDebugUnitTest
```

## Glossary contract (PR7)

Emitted event names must be ⊆ Phase A∪B in `docs/ANALYTICS_EVENT_GLOSSARY.md`
(plus `$set` person properties). Phase C is deferred to PR9 — façade methods for
Phase C events are no-ops. All captures go through `AnalyticsEmit` (allowlist gate).

## CI relevance

Covered by `unit-tests.yml` (`testDebugUnitTest`). Kover variant `merged` is
registered and included in root `:koverVerifyMerged` (with data/domain/home/rss/downloads).
Architecture script `scripts/ci/check-feature-no-posthog.sh` fails if feature modules
import PostHog or call `PostHog.capture`.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
