# `:core:network`

## Purpose

Owns the Retrofit API client (`BoxLoreApi`), `NetworkModule` wiring, and network DTOs (Podcast Index proxy payloads, content sections, sync models such as `HistoryItem`, etc.). Does **not** own Room, RSS feed parsing (`:core:rss`), Compose, or repositories.

## Public API

- `NetworkModule` / `BoxLoreApi` — OkHttp + Retrofit client factory and API surface
- Network model types under `cx.aswin.boxlore.core.network.model` (including `HistoryItem`, content-section DTOs, sync models)
- App Check / version header hooks used by the app

Do not recreate a second OkHttp/Retrofit stack in features — use `NetworkModule` / injected `BoxLoreApi` from composition.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/network/
  BoxLoreApi.kt
  NetworkModule.kt
  model/          # request/response DTOs
```

## Dependencies

- → `:core:model`
- OkHttp, Retrofit, Gson / kotlinx.serialization as configured in Gradle

Forbidden: network ↛ `:core:data`, `:core:database`, features, or designsystem.

## Threading / lifecycle

- HTTP on OkHttp dispatcher threads; Retrofit suspend APIs must be called from a coroutine (typically IO)
- `NetworkModule` client is process-scoped when constructed from Application / App Check setup in `:app`

## Persistence & identity

None (stateless HTTP). Auth/App Check tokens are runtime; do not persist API keys in this module. Base URL / public key come from `:app` `BuildConfig`.

## Testing notes

- JVM: request serialization under `src/test`
- MockWebServer contract tests: `BoxLoreApiContractTest` — fixtures in `src/test/resources/fixtures/`
- Prefer MockWebServer over hitting the live backend

```bash
./gradlew :core:network:testDebugUnitTest
```

## CI relevance

Exercised by `unit-tests.yml` (B1 network contracts).

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:data` README](../data/README.md) — catalog repositories that call this API
- [`:core:rss` README](../rss/README.md) — feed client (separate from this HTTP API)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase B1)
