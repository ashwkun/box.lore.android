# `:core:testing`

## Purpose

Shared JVM test fixtures and JUnit 5 helpers for feature/core unit tests. Not shipped in the app.

## Public API

- `MainDispatcherExtension` — installs a test Main dispatcher per test
- `TestFixtures` — minimal `Podcast` / `Episode` builders

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/testing/
```

## Dependencies

- → `:core:model`
- JUnit Jupiter, coroutines-test, Turbine, MockWebServer (api)

## Testing notes

- Consumed as `testImplementation(projects.core.testing)` from other modules
- No MockK

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
