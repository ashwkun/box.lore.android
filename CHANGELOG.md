# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Redesigned sleep timer nudge with dynamic-island style SleepTimerPopup component.
- Implemented Firebase App Check token pre-warming and auto-refresh mechanisms.
- Added AI-suggested search redirect chips, anonymous data disclosure, and non-Latin input tips to AI Onboarding.
- Created `SleepTimerConstants` shared module to deduplicate numeric sentinel values.
### Changed
- Isolated the Learn screen playback queue to prevent auto-refill of normal episodes.
### Fixed
- Fixed sleep timer nudge dismissal race condition when hidden mid-confirmation.
- Fixed transition stutters for the Home screen tab navigation.
- Fixed AppCheck telemetry initialization race condition on app startup.
- Redacted sensitive security tokens (App Check and API key) from OkHttp header logcat output.
- Fixed candidate cache type-mixing and delayed news-flag sync issues on podcast import.

## [v0.0.5] - 2026-07-08
### Added
- Migrated backend pipeline to `Xenova/bge-m3` embedding model for superior multilingual search support.
- Configured Qdrant index storage optimizations, including int8 scalar quantization and on-disk indexing configurations.
- Integrated telemetry session tracking and action counters for the Learn screen and bottom navigation.
- Created `toEpisode()` common model mapping helper in `EpisodeMapper.kt` in the `:core:data` module.
- Extracted shared `TrackScreenSession` composable in `LifecycleUtils.kt` to observe screen start/stop lifecycle events across screens without code duplication.
### Changed
- Optimized the Learn screen with centered empty state layouts, swipe history list navigation, and card restoration flow.
- Reduced background artwork blur on the Daily Briefing screen to 8.dp to ensure branding logo remains visible.
- Removed default candidate caps and episode limit counts across background sync and vectorization scripts (`sync-episodes.js`, `vectorize.js`, `vectorize-podcasts.js`).
- De-coupled `medium` column check from sync candidate selection in `sync-episodes.js`.
- Optimized Home screen transitions by deferring heavy below-the-fold content sections during slide navigation.
### Fixed
- Supported region code aliases (`uk`/`ind`) in Settings and Onboarding segmented control country selectors.
- Configured the explore screen semantic search results hero card to display a `"FEATURED RESULT"` badge.
- Recreated the active `MainActivity` immediately upon a successful JSON backup restore to update the active theme, region, and subscriptions reactively.


## [v0.0.4] - 2026-07-05
### Added
- Redesigned explore and curiosity card decks, ambient background color extraction, and pill card controls.
- Reworked downloads screen to feature collapsible sections, single-column lists, multi-select operations, and WorkManager purging.
- Refined mixtape layouts and scoring ranking algorithm.
- Added high-resolution artwork overrides and device pixel density image scaling.
- Added dismissible new episode banners on home feed.
- Added circular wavy play loader component for buffering.
### Fixed
- Fixed navigation backstack-awareness and correct active tab highlights.
- Fixed playback completed status replay bug and scroll stutter optimizations using JankStats.

## [v0.0.3] - 2026-06-27
### Changed
- Bumped version code to 3 (v0.0.3) due to package name changes for Google Play Store release.
- Preserve local show subscription timestamp to avoid resetting DB subscription date to 0 when loading show info details.
- Enable scoring boosts for notification-enabled (+30 pts) and auto-downloads enabled (+60/90 pts) shows.

## [v2.6.7] - 2026-06-23
### Changed
- Rebranded release.

## [v2.6.4] - 2026-06-15
### Added
- Added semantic search support and updated featured show referencing.
- Improved recommendation system and implemented UI optimizations.

## [v2.6.3] - 2026-06-12
### Added
- Material 3 UI refinements and onboarding back navigation improvements.
### Fixed
- Artwork loading fixes.

## [v2.6.2] - 2026-06-03
### Added
- Dynamic skip behavior settings in App Preferences ("Just Skip" vs "Mark Completed & Skip").
- Play All Floating Action Button (FAB) on Subscriptions tab.
- Granular completed episodes filtering across Home feeds, New episodes, and Show details screen.
- Headphone/Bluetooth double-click support mapped to Forward 30s/Back 10s.
- Swipe-away app task cleanup protection during active listening.
### Fixed
- Fixed recommendations caching logic and artwork rendering proxy fallback.

## [v2.6.1] - 2026-05-30
### Added
- Personalized recommendations engine ("For You" tab) based on played episodes, active subscriptions, and genres.
- Expanded recommendations metadata matching matching.
- Center-aligned capsule tab selector navigation.
- France region support with localized trending badges.
- Persistent show list sorting preferences across app launches.
### Fixed
- Resumed position recovery for partially-played audio tracks.

## [v2.5.0] - 2026-05-25
### Added
- Integrated dynamic mixtape queues and library section filters.
- Implemented serialization continuation listening logic.
- Built release-cadence estimator and frequency detail sheet.
- Added fullscreen 16:9 video player, synced live transcripts, clickable chapters, and promotional trailers.
- Implemented edge spelling correction workers and FTS5 SQLite pruning.

## [v2.1.0] - 2026-05-13
### Added
- Massive BoxCast 2.0 interface redesign.
- Subscriptions database backup and restore utility.
- Expanded transparency privacy settings.

## [v1.4.1] - 2026-05-04
### Added
- AI Insights dashboard and interactive telemetry analytics console.
