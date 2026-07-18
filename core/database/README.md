# `:core:database`

## Purpose

Owns the main Room database (`BoxLoreDatabase`), entities, DAOs, type converters, and migrations for podcasts, queue, history, downloads, and RSS episodes. Does **not** own repositories, ranking’s separate adaptive Room DB, playback, or workers.

## Public API

Stable types/entry points other modules may depend on:

- `BoxLoreDatabase` (+ migrations / `getInstance` factory)
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

Gradle edges (project + notable libs):

- → `:core:model`
- → `:core:network` (`QueueItem` ↔ `EpisodeItem`)
- Room (api runtime + ktx; ksp compiler)
- Gson (type converters)

Forbidden reverse edges: database ↛ `:core:data`, features, or designsystem.

## Testing notes

- No dedicated tests in this module yet; coverage lives in `:core:data` / feature JVM tests that exercise repositories against the DB.
- Prefer fakes at repository/port boundaries over hitting Room in unit tests unless using an in-memory DB.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md) for cross-module rules
- [`:core:data` README](../data/README.md) — repositories still compose this DB
