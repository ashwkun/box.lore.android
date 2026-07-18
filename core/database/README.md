# `:core:database`

## Purpose

Owns the main Room database (`BoxLoreDatabase`), entities, DAOs, type converters, and migrations for podcasts, queue, history, downloads, and RSS episodes. Does **not** own repositories, ranking’s separate adaptive Room DB (`:core:ranking`), playback, or workers.

## Public API

- `BoxLoreDatabase` (+ migrations / `getDatabase` factory)
- Entities: `PodcastEntity`, `ListeningHistoryEntity`, `DownloadedEpisodeEntity`, `RssEpisodeEntity`, `entities.QueueItem`
- DAOs: `PodcastDao`, `ListeningHistoryDao`, `DownloadedEpisodeDao`, `RssEpisodeDao`, `dao.QueueDao`
- `Converters` (Room type converters)

Package names stay `cx.aswin.boxlore.core.data.database` (no import renames). Do not rename the on-disk Room DB filename.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/database/
  BoxLoreDatabase.kt
  Converters.kt
  *Entity.kt / *Dao.kt
  entities/   # QueueItem
  dao/        # QueueDao
```

## Dependencies

- → `:core:model`
- → `:core:network` (`QueueItem` ↔ `EpisodeItem` debt)
- Room (api runtime + ktx; ksp compiler), Gson

Forbidden: database ↛ `:core:data`, features, or designsystem.

## Threading / lifecycle

- `BoxLoreDatabase.getDatabase` is process-singleton (Room builder exception to the no-`getInstance` rule — documented in architecture)
- DAO calls are suspend / Flow; run off Main when touching large result sets
- Application constructs via `AppContainer`; features must use ports (`LocalCatalogPort`, etc.), not inject the DB

## Persistence & identity

| Stable | Why |
| :--- | :--- |
| Filename `boxlore_database` | User data (legacy `boxcast_database` auto-renamed on first open) |
| Entity / table schemas + migrations | Install continuity |
| Package `cx.aswin.boxlore.core.data.database` | Import / FQCN stability |

Ranking DB (`adaptive_ranking_database`) is **not** in this module — see `:core:ranking`.

## Testing notes

### B4 — Room DAO / `includeAndroidResources`

This module sets `unitTests.isIncludeAndroidResources = true` and ships `PodcastDaoInMemoryTest` (upsert/get round-trip).

**If that suite fails to configure or run in CI**, documentation + RSS ID fixtures (`:core:rss` `RssIdGeneratorTest`) remain the B4 exit — do not force flaky Room JVM tests. `:core:data` keeps `includeAndroidResources = false` and uses Mockito DB doubles for catalog HTTP tests.

```bash
./gradlew :core:database:testDebugUnitTest
```

Prefer fakes at repository/port boundaries for feature tests.

## CI relevance

Exercised by `unit-tests.yml` when DAO tests stay green.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md) — Room / Robolectric constraints
- [`:core:data` README](../data/README.md)
- [`:core:ranking` README](../ranking/README.md) — separate adaptive DB
