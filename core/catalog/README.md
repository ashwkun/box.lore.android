# `:core:catalog`

## Purpose

Owns catalog orchestration: Podcast Index access through `PodcastRepository`, subscriptions, chapters, transcripts, personalized content sections, cross-promotion, consent helpers, install-referrer handling, backup and restore, and shared dependency bridges used by workers and services. It does not own playback, download workers, Compose UI, Room schemas, RSS internals, or ranking model storage.

## Public API

- `PodcastRepository` coordinates Podcast Index calls, recommendation endpoints, content sections, and RSS delegation.
- `SubscriptionRepository`, `ChapterRepository`, and `TranscriptRepository` expose catalog-adjacent data operations.
- `content.ContentOrchestrator`, `GroupedContentSectionProvider`, `ContentContextEngine`, and related content contracts assemble personalized Home and discovery sections.
- `home.HomePersonalizationCoordinator` retrieves `/home/candidates/v1` pools, applies ≥4h module caching via `PodcastRepository.getHomeCandidatesV1`, and allocates Taste / Because You Like / Mission with cross-section de-duplication. Mode, meaningful-play, and BYL anchor helpers live under `home/`.
- `home.HomeSlateQualityLogic.compute` derives an **observational-only** quality aggregate (candidate coverage, duplicate rate, novelty share, fallback/cache/latency buckets) per slate load for the `home_slate_quality_snapshot` analytics event; catalog computes the bucketed value object (`:core:model`'s `HomeSlateQualityTelemetry`) but never calls `:core:analytics` itself — features track it.
- `home.recommendationLanguagesForCountry(country)` maps a listener's region to an ordered language list for both `home/candidates/v1` and `recommendations/v2` requests, replacing hardcoded English-only language lists.
- `backup.LibraryBackupManager` imports and exports library data, OPML, listening history, and ranking backup payloads.
- `SharedAppDependencies` and `SharedAppDependenciesHolder` expose application-scoped instances to workers and services.
- `InstallReferrerManager` parses Play Install Referrer deep links and exposes optional `onInstallReferrerResolved` (channel + raw referrer). `:app` wires that callback into analytics; catalog must not depend on `:core:analytics`.
- `RoomLocalCatalog` implements `LocalCatalogPort`; `RoomEpisodeOfflineLookup` implements `EpisodeOfflineLookupPort`.
- `ports.ListeningHistoryBackupPort` and `ports.SmartDownloadSyncPort` keep backup and download seams out of UI modules.
- `:core:rss`, `:core:domain`, `:core:database`, and `:core:prefs` are re-exported where existing public signatures require those types.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/catalog/
  PodcastRepository.kt
  PodcastRepositoryContentCache.kt
  PodcastRepositoryContentMapping.kt
  PodcastRepositoryMappers.kt
  PodcastRepositoryNetworkLookups.kt
  PodcastRepositoryRecommendations.kt
  PodcastRepositoryStreams.kt
  EpisodeMapper.kt
  SubscriptionRepository.kt
  ChapterRepository.kt
  TranscriptRepository.kt
  SharedAppDependencies.kt
  RoomLocalCatalog.kt
  RoomEpisodeOfflineLookup.kt
  EngagementPromptCoordinator.kt
  InstallReferrerManager.kt
  backup/
  content/
  home/
  crosspromo/
  ports/
  privacy/
```

Main Kotlin files should remain below 1000 lines; extracted helpers keep repository mapping, network lookups, content cache, recommendations, Home candidate coordination, and stream handling reviewable.

## Dependencies

- Project dependencies: `:core:model`, `:core:network`, `:core:domain`, `:core:database`, `:core:prefs`, `:core:rss`, and `:core:ranking`.
- Libraries: Retrofit, OkHttp, Gson, coroutines, DataStore, Firebase Database, Firebase Messaging, and Install Referrer.
- Reverse-edge rule: catalog must not depend on playback, downloads, designsystem, analytics, or feature modules.

## Threading / lifecycle

- Production repositories are application-scoped through `AppContainer` and `SharedAppDependenciesHolder`.
- Network and database work uses suspend APIs and background dispatchers at repository boundaries.
- Workers and services must use the installed holder instead of constructing independent catalog, RSS, or ranking graphs.

## Persistence & identity

- Main Room database identity is owned by `:core:database` and exposed through catalog APIs.
- User preference and cache file names are owned by `:core:prefs` and catalog cache code.
- In-process `PodcastRepository` caches include recommendations, Because You Like, and Home candidates (`≥4h` TTL for `home/candidates/v1`).
- RSS podcast IDs and negative episode IDs are owned by `:core:rss`.
- Backup JSON field names and backup version fields must remain restore-compatible.
- Package root is `cx.aswin.boxlore.core.catalog`.

## Testing notes

- Unit tests live under `core/catalog/src/test`.
- Existing coverage includes `PodcastRepositoryCatalogTest`, `InstallReferrerManager` channel derivation / attribution callback seams, content orchestration tests, content signal enrichment, grouped sections, recent section intent storage, cross-promotion detection, transcript behavior, dependency-holder behavior, and Home personalization helpers (`HomeCandidatesRequestBuilderTest`, `HomePersonalizationLogicTest`, `HomeSlateQualityLogicTest`, `PodcastRepositoryRecommendationsLanguageTest`).
- RSS ID and matcher tests live in `:core:rss`; smart-queue tests live in `:core:playback`.

```bash
./gradlew :core:catalog:testDebugUnitTest
./gradlew :core:catalog:testDebugUnitTest --tests 'cx.aswin.boxlore.core.catalog.PodcastRepositoryCatalogTest'
```

## CI relevance

- `unit-tests.yml` runs catalog JVM tests.
- The root Kover merged verification includes this module.
- Architecture guards verify README presence and selected dependency boundaries.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/recommendation-system.md`](../../docs/recommendation-system.md)
- [`:core:rss` README](../rss/README.md)
- [`:core:ranking` README](../ranking/README.md)
- [`:core:prefs` README](../prefs/README.md)
- [`:core:playback` README](../playback/README.md)
