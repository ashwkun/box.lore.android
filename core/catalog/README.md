# `:core:catalog`

## Purpose

Owns catalog orchestration: Podcast Index access through `PodcastRepository`, subscriptions, chapters, transcripts, personalized content sections, cross-promotion, consent helpers, install-referrer handling, backup and restore, and shared dependency bridges used by workers and services. It does not own playback, download workers, Compose UI, Room schemas, RSS internals, or ranking model storage.

## Public API

- `PodcastRepository` coordinates Podcast Index calls, recommendation endpoints, content sections, and RSS delegation.
- `SubscriptionRepository`, `ChapterRepository`, and `TranscriptRepository` expose catalog-adjacent data operations.
- `content.ContentOrchestrator`, `GroupedContentSectionProvider`, `ContentContextEngine`, and related content contracts assemble personalized Home and discovery sections.
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
  home/                 # HomeCandidatesRequestBuilder + HomePersonalizationCoordinator
  crosspromo/
  ports/
  privacy/
```

Public Home APIs:

- `recommendationLanguagesForCountry` maps region → language tags for retrieval (not English-only).
- `PodcastRepository.getHomeCandidatesV1` calls `POST /home/candidates/v1` with a ≥4h module cache; null body means fall back to legacy recommendation routes.
- `home.HomePersonalizationCoordinator` builds requests and maps module pools for Home slate allocation.
Main Kotlin files should remain below 1000 lines; extracted helpers keep repository mapping, network lookups, content cache, recommendations, and stream handling reviewable.

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
- RSS podcast IDs and negative episode IDs are owned by `:core:rss`.
- Backup JSON field names and backup version fields must remain restore-compatible.
- Package root is `cx.aswin.boxlore.core.catalog`.

## Testing notes

- Unit tests live under `core/catalog/src/test`.
- Existing coverage includes `PodcastRepositoryCatalogTest`, `InstallReferrerManager` channel derivation / attribution callback seams, content orchestration tests, content signal enrichment, grouped sections, recent section intent storage, cross-promotion detection, transcript behavior, and dependency-holder behavior.
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
- [`:core:rss` README](../rss/README.md)
- [`:core:ranking` README](../ranking/README.md)
- [`:core:prefs` README](../prefs/README.md)
- [`:core:playback` README](../playback/README.md)
