# `:core:catalog`

## Purpose

**Catalog and orchestration layer.** Owns the Podcast Index catalog (`PodcastRepository`), subscriptions, content sections, cross-promo, engagement, backup/restore, and the shared-deps composition bridge for workers and Media3 services. It is **not** a junk drawer — every type here is a catalog, subscription, or orchestration concern.

Gradle id is `:core:catalog`. **Phase 2 target package root:** `cx.aswin.boxlore.core.catalog` (PR10). Until then, sources may still live under transitional `cx.aswin.boxlore.core.data.*` with upgrade failsafes.

Extracted subsystems now live in dedicated modules:

| Subsystem | Module |
| :--- | :--- |
| RSS feed fetch / parse / IDs / `RssPodcastRepository` | `:core:rss` (re-exported via `api`) |
| Adaptive ranking / LinUCB / feedback loop | `:core:ranking` (`implementation` — declare directly in consumers) |
| Analytics façade (`AnalyticsHelper`, `Analytics`) | `:core:analytics` (not re-exported; declare directly) |
| Smart queue / queue math / mixtape | `:core:playback` |
| Download worker stack | `:core:downloads` |
| Playback / queue / Media3 services | `:core:playback` |
| DataStore + SharedPrefs | `:core:prefs` |
| Main Room database | `:core:database` |

## Public API

- **Catalog:** `PodcastRepository` (Podcast Index HTTP + RSS delegate via `RssPodcastRepository`), `SubscriptionRepository`, `ChapterRepository`, `TranscriptRepository`
- **Content sections:** `content/ContentOrchestrator`, `content/GroupedContentSectionProvider`, `content/ContentContextEngine`
- **Backup/restore:** `backup/LibraryBackupManager` — JSON + OPML export/import, ranking backup, RSS re-import. Wire type is `BoxLoreBackup` (Gson field names / `version` unchanged for restore compatibility).
- **Shared-deps bridge:**
  - `SharedAppDependencies` — interface of Application-scoped instances consumed by workers/services via `SharedAppDependenciesHolder` (includes ranking types; consumers that touch those members must depend on `:core:ranking`)
  - `SharedAppDependenciesHolder` — `@Volatile` install + `require()` (throws if unset)
- **Re-exported (api) subsystems:** types from `:core:rss`, `:core:domain`, `:core:database`, `:core:prefs`
- **Data-only ports:** `ports.ListeningHistoryBackupPort`, `ports.SmartDownloadSyncPort`
- **Domain port impls:** `RoomLocalCatalog` (`LocalCatalogPort`), `RoomEpisodeOfflineLookup` (`EpisodeOfflineLookupPort`)

> `ports.DownloadCacheRelinker` lives in `:core:rss` (package `cx.aswin.boxlore.core.data.ports`; re-exported via `api(core:rss)`).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  PodcastRepository.kt
  SubscriptionRepository.kt
  ChapterRepository.kt / TranscriptRepository.kt
  SharedAppDependencies.kt
  EngagementPromptCoordinator.kt / InstallReferrerManager.kt
  content/                       # Personalised home sections
  crosspromo/                    # Cross-promotion
  backup/                        # LibraryBackupManager
  privacy/                       # ConsentManager
  ports/                         # SmartDownloadSyncPort, ListeningHistoryBackupPort
```

## Dependencies

- → `:core:rss` (`api`), `:core:ranking` (`implementation`)
- → `:core:domain` (`api`), `:core:prefs` (`api`), `:core:database` (`api`)
- → `:core:model`, `:core:network` (internal)
- → Firebase (database + messaging), Retrofit, OkHttp, Gson, DataStore

Forbidden: `:core:catalog` **must not** depend on `:core:playback`, `:core:designsystem`, `:core:downloads`, or `:core:analytics`.

## Threading / lifecycle

- Repositories are Application-scoped when obtained from `SharedAppDependenciesHolder` / `AppContainer`
- Workers must not construct a second Podcast/ranking/RSS graph — obtain instances from the holder

## Persistence & identity

| Stable | Why |
| :--- | :--- |
| DataStore `user_preferences` / SharedPrefs `boxcast_prefs` (owned by `:core:prefs`) | Existing installs |
| Main Room DB filename (owned by `:core:database`) | User data |
| `rss:` podcast IDs and negative episode IDs (owned by `:core:rss`) | Catalog identity |

## Testing notes

JVM unit tests under `src/test`:

- **B2 catalog (MockWebServer):** `PodcastRepositoryCatalogTest` — fixtures under `src/test/resources/fixtures/`
- `content/ContentOrchestratorTest`, `ContentSignalEnrichmentTest`, `GroupedContentSectionsTest`, `RecentSectionIntentStoreTest`
- `crosspromo/CrossPromotionDetectorTest`, `TranscriptRepositoryTest`
- `SharedAppDependenciesHolderTest`

Smart-queue JVM tests (`QueueMathTest`, `QueueSkipMemoryTest`, `SmartQueueEngineTest`) live in `:core:playback`.
RSS ID/matcher tests live in `:core:rss`. Production RSS uses create+install from `AppContainer`; catalog JVM tests use `RssPodcastRepository.createForTests`.

```bash
./gradlew :core:catalog:testDebugUnitTest
./gradlew :core:catalog:testDebugUnitTest --tests 'cx.aswin.boxlore.core.data.PodcastRepositoryCatalogTest'
```

## CI relevance

Exercised by `unit-tests.yml` (`testDebugUnitTest`). Kover `merged` variant participates in `:koverVerifyMerged`.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`:core:rss` README](../rss/README.md)
- [`:core:ranking` README](../ranking/README.md)
- [`:core:downloads` README](../downloads/README.md)
- [`:core:domain` README](../domain/README.md)
- [`:core:playback` README](../playback/README.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A6)
