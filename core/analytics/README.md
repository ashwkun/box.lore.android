# `:core:analytics`

## Purpose

Owns analytics event capture and non-fatal error reporting for Boxlore. The module wraps PostHog behind application interfaces so callers do not depend on the SDK directly. It does not initialize PostHog, own UI, own repositories, or persist data beyond analytics first-launch preferences.

## Public API

- `Analytics` is the interface used by production code and tests.
- `AnalyticsHelper` is the PostHog-backed production singleton.
- `RecordingAnalytics` is an in-memory test double.
- `ErrorReporter` is the non-fatal error facade; `:app` may install Crashlytics-backed reporting.
- `PendingEntryPoint` bridges playback entry-point context across MediaController boundaries.
- `PlayerSessionAggregator` batches per-episode player interaction events.
- Event names and property expectations are documented in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).
- Glossary façades include growth/session (`trackOnboardingAbandoned`, `trackSessionRestorePrompt`), library ops (`trackDownloadRequested`, `trackShareContent`, `trackBackupRestoreResult`, `trackQueueModified`), discovery (`trackExploreSearchPerformed` with `search_mode`, `trackSearchResultTapped`), and onboarding (`trackOnboardingStepViewed` with optional `step_index`).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/analytics/
  Analytics.kt
  AnalyticsHelper.kt                                   # PostHog singleton + track façades
  AnalyticsEmit.kt
  *AnalyticsTracks.kt                                  # domain emit implementations
  ErrorReporter.kt
  PendingEntryPoint.kt
  RecordingAnalytics.kt
  SessionAggregator.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:prefs`.
- Libraries: PostHog Android SDK and AndroidX core.
- Reverse-edge rule: analytics must not depend on catalog, database, network, playback, downloads, designsystem, or feature modules. Callers that emit events declare this module directly.

## Threading / lifecycle

- `AnalyticsHelper` is process-scoped and its methods are callable from any thread; the SDK handles internal dispatch.
- `PendingEntryPoint` and `PlayerSessionAggregator` use synchronized or volatile state for cross-thread access.
- `RecordingAnalytics.flush()` is a no-op suitable for tests.

## Persistence & identity

- SharedPreferences file `boxlore_analytics_prefs` stores the first-launch flag and migrates from `boxcast_analytics_prefs` through `PrefsFileMigrator`.
- Preference key `is_first_launch` must remain stable.
- Event identity must remain aligned with [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Lifecycle / volume (no double-count)

- Opens / backgrounds / install **counts** come from PostHog SDK autocapture (`Application Opened`, `Application Backgrounded`, `Application Installed`) with `captureApplicationLifecycleEvents = true`.
- Do **not** emit glossary `app_open`, `app_background`, or volume `install_attributed`.
- Install channel: `$set_once` person `install_channel` only (`trackInstallChannelAttributed`).
- Deep links: custom `deep_link_opened` (`captureDeepLinks = false`).
- Weekly Pulse KPIs stay on **Application Opened** / **Application Installed**.
- Coverage inventory: [`docs/analytics/glossary_emission_coverage.csv`](../../docs/analytics/glossary_emission_coverage.csv) (`emission` | `sdk_backed` | `person_props_only`).

## Testing notes

- Unit tests live under `src/test/java/cx/aswin/boxlore/core/analytics`.
- Use `RecordingAnalytics` when testing classes that accept `Analytics`.
- `GlossaryAllEventsEmissionTest` covers every `emission:` inventory row; `LifecycleSdkMappingTest` / `InstallChannelAttributionTest` assert no dual open/install emits.
- Architecture guards in `:core:testing` enforce CSV ↔ allowlist ↔ inventory parity.

```bash
./gradlew :core:analytics:testDebugUnitTest
./gradlew :core:testing:testDebugUnitTest --tests '*GlossaryCoverageGuardTest'
```

## CI relevance

- `unit-tests.yml` runs the module JVM tests.
- The root Kover merged verification includes this module.
- `scripts/ci/check-feature-no-posthog.sh` prevents feature modules from importing or calling PostHog directly.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`:core:prefs` README](../prefs/README.md)
