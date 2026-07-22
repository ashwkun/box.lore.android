# `:core:network`

## Purpose

Owns the Retrofit API boundary, OkHttp/Retrofit construction, request and response DTOs, and network serialization contracts for Boxlore's HTTP API. It does not own repositories, RSS feed parsing, Room persistence, Compose UI, or feature workflows.

## Public API

- `BoxLoreApi` defines the Retrofit service surface.
- `NetworkModule` creates OkHttp, Retrofit, and related network clients.
- DTOs under `cx.aswin.boxlore.core.network.model`, including content-section, home-candidates v1, recommendation, history, sync, and request payload models.
- App Check, app version, public-key, and device-header hooks used by application wiring.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/network/
  BoxLoreApi.kt
  NetworkModule.kt
  model/
```

## Dependencies

- Project dependencies: `:core:model`.
- Libraries: Retrofit, Kotlinx serialization, OkHttp, OkHttp logging interceptor, Gson, AndroidX annotation, and coroutines.
- Reverse-edge rule: network must not depend on catalog, database, playback, downloads, designsystem, or feature modules.

## Threading / lifecycle

- OkHttp uses its own dispatcher threads.
- Retrofit suspend functions must be called from coroutines, normally from repository IO paths.
- Production clients are process-scoped when created by application wiring.

## Persistence & identity

- No user data is persisted by this module.
- Base URL, public key, app version, App Check tokens, and device identifiers are supplied at runtime by app or repository wiring.
- API path and payload shape changes should be tested as contract changes.

## Testing notes

- Unit tests live under `core/network/src/test`.
- `BoxLoreApiContractTest` uses MockWebServer fixtures for endpoint contracts,
  including `POST /home/candidates/v1`.
- `ContentSectionsV1RequestSerializationTest` covers request serialization.
- `HomeCandidatesV1SerializationTest` covers home-candidates request/response shapes.
- Prefer MockWebServer over live backend calls.

```bash
./gradlew :core:network:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs network JVM contract tests.
- Network DTO compile failures block catalog and feature modules that map API responses.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:rss` README](../rss/README.md)
