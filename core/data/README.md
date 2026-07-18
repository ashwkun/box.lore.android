# `:core:data`

## Purpose

**Catalog and orchestration layer.** Owns the Podcast Index catalog (`PodcastRepository`), subscriptions, smart queue logic, content sections, cross-promo, engagement, backup/restore, and the shared-deps composition bridge for workers and Media3 services. It is **not** a junk drawer — every type here is a catalog, subscription, or orchestration concern.

**Rename to `:core:catalog` is deferred** — keep the Gradle id `:core:data` until a dedicated rename PR; treat this module as catalog-only in docs and dependency rules.

Extracted subsystems now live in dedicated modules (re-exported via `api` so existing import paths continue to compile):

| Subsystem | Module |
| :--- | :--- |
| RSS feed fetch / parse / IDs / `RssPodcastRepository` | `:core:rss` |
| Adaptive ranking / LinUCB / feedback loop | `:core:ranking` |
| Analytics façade (`AnalyticsHelper`, `Analytics`) | `:core:analytics` |
| Download worker stack | `:core:downloads` |
| Playback / queue / Media3 services | `:core:playback` |
| DataStore + SharedPrefs | `:core:prefs` |
| Main Room database | `:core:database` |

## Public API

- **Catalog:** `PodcastRepository` (Podcast Index HTTP + RSS delegate via `RssPodcastRepository`), `SubscriptionRepository`, `ChapterRepository`, `TranscriptRepository`
- **Smart queue helpers:** `QueueMath`, `QueueSkipMemory`, `SmartQueueEngine` / `SmartQueueSources`, `MixtapeEngine`
- **Content sections:** `content/ContentOrchestrator`, `content/GroupedContentSectionProvider`, `content/ContentContextEngine`
- **Backup/restore:** `backup/LibraryBackupManager` — JSON + OPML export/import, ranking backup, RSS re-import
- **Shared-deps bridge:**
  - `SharedAppDependencies` — interface of Application-scoped instances consumed by workers/services via `SharedAppDependenciesHolder`
  - `SharedAppDependenciesHolder` — `@Volatile` install + `require()` (throws if unset)
- **Re-exported (api) subsystems:** types from `:core:rss`, `:core:analytics`, `:core:ranking`, `:core:domain`, `:core:database`, `:core:prefs`
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
  SmartQueueEngine.kt / SmartQueueSources.kt / QueueMath.kt / QueueSkipMemory.kt / MixtapeEngine.kt
  EngagementPromptCoordinator.kt / InstallReferrerManager.kt
  content/                       # Personalised home sections
  crosspromo/                    # Cross-promotion
  backup/                        # LibraryBackupManager
  privacy/                       # ConsentManager
  ports/                         # SmartDownloadSyncPort, ListeningHistoryBackupPort
```

## Dependencies

- → `:core:rss` (`api`), `:core:analytics` (`api`), `:core:ranking` (`api`)
- → `:core:domain` (`api`), `:core:prefs` (`api`), `:core:database` (`api`)
- → `:core:model`, `:core:network` (internal)
- → Firebase (database + messaging), Retrofit, OkHttp, Gson, DataStore

Forbidden: `:core:data` **must not** depend on `:core:playback`, `:core:designsystem`, or `:core:downloads`.

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
- `QueueMathTest`, `QueueSkipMemoryTest`, `SmartQueueEngineTest`
- `content/ContentOrchestratorTest`, `ContentSignalEnrichmentTest`, `GroupedContentSectionsTest`, `RecentSectionIntentStoreTest`
- `crosspromo/CrossPromotionDetectorTest`, `TranscriptRepositoryTest`
- `SharedAppDependenciesHolderTest`

RSS ID/matcher tests live in `:core:rss`. Production RSS uses create+install from `AppContainer`; catalog JVM tests use `RssPodcastRepository.createForTests`.

```bash
./gradlew :core:data:testDebugUnitTest
./gradlew :core:data:testDebugUnitTest --tests 'cx.aswin.boxlore.core.data.PodcastRepositoryCatalogTest'
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
