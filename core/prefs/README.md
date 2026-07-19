# `:core:prefs`

## Purpose

Owns user preference persistence: DataStore `user_preferences` via `UserPreferencesRepository`, theme fast-cache SharedPreferences (`boxlore_theme_fast_cache`, migrated from `boxcast_theme_fast_cache`), and the typed façade `BoxcastPrefs` over `boxlore_prefs` (migrated from `boxcast_prefs`).

Also owns [`PrefsFileMigrator`](src/main/java/cx/aswin/boxlore/core/prefs/PrefsFileMigrator.kt) — dual-read SharedPreferences file migration used by prefs, analytics, playback, catalog, and app.

Does **not** own analytics prefs file writers (analytics module), player prefs writers (playback/catalog), privacy consent DataStore, ranking runtime SharedPreferences, or catalog cache files — those call `PrefsFileMigrator.open` where applicable.

## Public API

Stable types other modules may depend on:

- `UserPreferencesRepository` — DataStore-backed settings (theme, region, smart downloads, skip, etc.)
- `Context.userPreferencesDataStore` — named DataStore delegate (`user_preferences`)
- `BoxcastPrefs` — typed API for `boxlore_prefs` keys (onboarding, genres, recommendation caches, learn curiosity, learner-log gate)
- `PrefsFileMigrator` — migrate/open helper for `boxcast_*` → `boxlore_*` files
- `PlaybackSkipBounds` / `EngagementPromptConstants` — shared sanitize/threshold constants

**Package root:** `cx.aswin.boxlore.core.prefs` (matches module). Pref **keys** stay stable; **files** migrate via dual-read failsafe — do not blind-rename strings. Do **not** recreate a parallel SharedPreferences client in features — call the prefs façade.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/prefs/
  UserPreferencesRepository.kt
  BoxcastPrefs.kt
  PrefsFileMigrator.kt
  …
```

## Dependencies

- → `:core:model` (api)

## Identity / migration

| File | Status |
|:--|:--|
| `boxlore_prefs` / `boxlore_theme_fast_cache` | Canonical (migrated from `boxcast_*`) |
| DataStore `user_preferences` | Unchanged |

See [`docs/PACKAGE_MIGRATION_MAP.md`](../../docs/PACKAGE_MIGRATION_MAP.md).
