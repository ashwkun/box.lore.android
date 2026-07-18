# `:core:testing`

## Purpose

Shared JVM test fixtures and JUnit 5 helpers for feature/core unit tests. Hosts **architecture-as-code** (Konsist / filesystem) guards. Not shipped in the app APK.

## Public API

- `MainDispatcherExtension` — installs a test Main dispatcher per test
- `TestFixtures` — minimal `Podcast` / `Episode` builders

Architecture guards live under `src/test` (`ArchitectureGuardTest`) — run via this module’s unit-test task, not as production API.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/testing/
  MainDispatcherExtension.kt
  TestFixtures.kt
src/test/java/cx/aswin/boxlore/core/testing/architecture/
  ArchitectureGuardTest.kt   # Konsist + filesystem rules
```

## Dependencies

- → `:core:model`
- JUnit Jupiter, coroutines-test, Turbine, MockWebServer (api)
- Test-only: Konsist

Consumed as `testImplementation(projects.core.testing)` from other modules.

## Threading / lifecycle

- `MainDispatcherExtension` swaps `Dispatchers.Main` for the duration of each test
- No Application / Android lifecycle objects

## Persistence & identity

None. Guards may *read* `settings.gradle.kts` / module folders / README presence; they do not own prefs or DBs.

## Testing notes

- Architecture guards: feature isolation, `getInstance` allowlist, `:core:data` ↛ designsystem, every included app/core/feature module has `README.md`
- Unit tests set working directory to the Gradle root so Konsist/filesystem rules can read project files
- No MockK

```bash
./gradlew :core:testing:testDebugUnitTest
```

## CI relevance

Runs in `unit-tests.yml` as part of root `testDebugUnitTest` (and via `:core:testing:testDebugUnitTest`). Complements `scripts/ci/check-feature-no-boxlore-database.sh`.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase B8)
