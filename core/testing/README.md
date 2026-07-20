# `:core:testing`

## Purpose

Owns shared JVM test fixtures, dispatcher helpers, and architecture guards used by core and feature modules. It is a test-support module and is not shipped in the app APK.

## Public API

- `MainDispatcherExtension` installs a test main dispatcher for a test scope.
- `TestFixtures` provides minimal podcast and episode builders.
- Test dependencies such as JUnit Jupiter, coroutines-test, Turbine, and MockWebServer are exposed for modules that declare `testImplementation(projects.core.testing)`.
- Architecture guards under `src/test` validate project structure and dependency rules.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/testing/
  MainDispatcherExtension.kt
  TestFixtures.kt
src/test/java/cx/aswin/boxlore/core/testing/architecture/
  ArchitectureGuardTest.kt
  GlossaryCoverageGuardTest.kt   # CSV ↔ AnalyticsGlossary ↔ emission inventory
```

## Dependencies

- Project dependencies: `:core:model`.
- Libraries exposed for tests: JUnit Jupiter, coroutines-test, Turbine, and MockWebServer.
- Test-only libraries: Konsist.
- Reverse-edge rule: production modules may use this only from test configurations.

## Threading / lifecycle

- `MainDispatcherExtension` swaps `Dispatchers.Main` for the duration of each test and restores it afterward.
- No Android application or activity lifecycle objects are owned here.

## Persistence & identity

- No app persistence is owned here.
- Architecture tests read project files such as `settings.gradle.kts` and module README locations but do not define app storage identity.

## Testing notes

- Run this module to execute architecture guards.
- Guards cover feature isolation, selected singleton allowlists, catalog-to-designsystem boundaries, direct database access restrictions, and README presence for included app/core/feature modules.
- `GlossaryCoverageGuardTest` asserts every `docs/analytics/event_glossary.csv` name is in `AnalyticsGlossary`, and every name has a row in `docs/analytics/glossary_emission_coverage.csv` with mode `emission:*`, `sdk_backed:*`, or `person_props_only` (opens/installs must not be dual-counted as custom emits).
- Do **not** add `api(projects.core.analytics)` here — `:core:analytics` already `testImplementation`s this module (cycle risk). Emission suites live in `:core:analytics`.

```bash
./gradlew :core:testing:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs the architecture guards with the root JVM suite.
- The guards complement shell-based architecture checks under `scripts/ci`.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:model` README](../model/README.md)
