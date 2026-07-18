# `:core:rss`

## Purpose

Owns the complete RSS podcast stack: feed fetching, parsing, ID generation, episode catalog management, and the RSS subscription port implementation. Deliberately does **not** own download management (lives in `:core:downloads`) or the Podcast Index API client (lives in `:core:network`).

`:core:data` re-exports this module via `api(projects.core.rss)` so existing callers of `cx.aswin.boxlore.core.data.RssPodcastRepository` continue to compile without adding a direct `:core:rss` dependency.

## Public API

- **`RssFeedClient`** — HTTP fetch, conditional HEAD checks (ETag / Last-Modified), dual-parser (rss-parser library + custom XmlPullParser). Returns `RssFetchResult` / `ParsedRssFeed` / `RssFreshnessResult`.
- **`RssPodcastRepository`** — implements `RssSubscriptionPort`; add/confirm/refresh subscriptions; episode catalog CRUD; background freshness checks. Singleton: call only via `AppContainer`; never construct ad hoc.
- **`RssIdGenerator`** — deterministic, FQCN-stable IDs:
  - `rss:` namespace for podcast IDs (`rss:<sha256-hex>`)
  - Negative `Long` IDs for episodes (collision-free with positive Podcast Index IDs)
  - See [Persistence & identity](#persistence--identity) for ID rules
- **`RssSourceMatcher`** — heuristic episode/show matching for Podcast Index migration (`feedIdentityMatches`, `likelySameShow`, `findMatchingEpisode`)
- **`ports.DownloadCacheRelinker`** — `fun interface` injected into `RssPodcastRepository` by `AppContainer` so the RSS module does not compile-depend on `:core:downloads`

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  RssFeedClient.kt          # HTTP + XML parsing; RssIdGenerator + RssSourceMatcher inside
  RssPodcastRepository.kt   # RssSubscriptionPort impl; escapeForSqlLike helper
  ports/
    DownloadCacheRelinker.kt  # fun interface — implemented in :core:downloads, wired by AppContainer
```

> **Package note:** all sources keep the package `cx.aswin.boxlore.core.data` (not `…core.rss`). This is intentional — FQCN / ID opacity stability for persisted references.

## Dependencies

- → `:core:database` (`api` — re-exports `BoxLoreDatabase`, `PodcastEntity`, `RssEpisodeEntity`, …)
- → `:core:domain` (`api` — re-exports `RssSubscriptionPort`, `RssSubscriptionResult`)
- → `:core:model` (internal — `Episode`, `Podcast`, `Person`, `Transcript`)
- → `com.prof18.rssparser` — Atom/RSS library parser path
- → `com.squareup.okhttp3:okhttp` — HTTP fetch + HEAD validators
- → Firebase Cloud Messaging — topic unsubscription on Podcast Index link migration

Forbidden edges: `core:rss` **must not** depend on `:core:downloads` (cycle), `:core:playback`, `:core:designsystem`, or any `:feature:*` module.

## Threading / lifecycle

- Application-scoped via `AppContainer` **create + install** (not ad-hoc construction). Workers/services use `SharedAppDependenciesHolder.require().rssPodcastRepository`
- All suspend functions dispatch to `Dispatchers.IO`. Freshness sweeps use `Semaphore(4)` for concurrent HEAD caps
- `refreshLocks` — per-podcast `Mutex` prevents duplicate concurrent catalog refreshes

## Persistence & identity

These identifiers are persisted in Room and must never change:

| Stable value | Why |
| :--- | :--- |
| Podcast IDs: `rss:<sha256-hex>` | Room `podcasts` table PK; user subscriptions |
| Episode IDs: negative `Long` string | Room `rss_episodes` PK; listening history FK; download FK |
| Package `cx.aswin.boxlore.core.data` | Opaque references across modules |

**Negative episode IDs:** `RssIdGenerator.episodeIdForPodcast` maps the first 8 bytes of `SHA-256(podcastId + "\0" + identity)` to a positive `Long`, then negates it (never `0` / `Long.MIN_VALUE`; never collides with positive Podcast Index IDs).

**`rss:` namespace:** 64-hex SHA-256 of the normalised, fragment-stripped feed URL (lower-case host).

**DownloadCacheRelinker:** `AppContainer` wires `DownloadRepository.relinkDownloadCache` after both repos exist so RSS does not compile-depend on `:core:downloads`.

## Testing notes

JVM unit tests under `src/test` cover deterministic ID contracts and episode matching heuristics:

- `RssIdGeneratorTest` — `rss:` prefix, HTTPS enforcement, deterministic negative episode IDs, collision-freedom with Podcast Index IDs, cross-feed independence
- `RssSourceMatcherTest` — URL canonical matching, GUID identity, author conflict rejection, enclosure URL priority, ambiguous title handling

```bash
./gradlew :core:rss:testDebugUnitTest
```

## CI relevance

Included in the unit-test CI job (`testDebugUnitTest`). Kover `merged` variant is added to the project-level coverage report.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:data` README](../data/README.md) — catalog/orchestration layer that re-exports this module
- [`:core:downloads` README](../downloads/README.md) — `DownloadCacheRelinker` implementation lives here
- [`:core:domain` README](../domain/README.md) — `RssSubscriptionPort` definition
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A6a)
