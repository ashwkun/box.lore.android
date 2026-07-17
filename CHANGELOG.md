# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

> **AI-generated summaries:** May contain mistakes. Verify details against the linked pull requests.

## [Unreleased]

### Changed
- Upgrade @libsql/client from 0.17.0 to 0.17.4 in scripts ([#894](https://github.com/ashwkun/boxlore/pull/894)) <!-- impact:no-user-impact -->
### Security
- Pinned patched transitive dependencies (Netty 4.1.136, BouncyCastle 1.84, jose4j 0.9.6, jdom2 2.0.6.1, commons‑compress 1.26.2, protobuf 3.25.5) via settings.gradle.kts and overridden protobufjs 7.6.5 and uuid 11.1.1 in scripts to clear Dependabot Maven alerts and npm audit security findings ([#896](https://github.com/ashwkun/boxlore/pull/896)) <!-- impact:no-user-impact -->
## [v0.0.10] - 2026-07-17

### Added
- Recommendation‑driven discovery sections on Home replace time‑of‑day rails; Adaptive Learner debug inspector with genre‑affinity graphs (excluding placeholder Podcast); bounded aggregate summaries (genre affinity, duration, maturity, novelty, recent‑section) for personalization; stale‑while‑revalidate caching for discovery rails with skeleton placeholders. ([#882](https://github.com/ashwkun/boxlore/pull/882)) <!-- impact:user-impact-high+backend-change -->
- Shared ImportLoaderView with morphing animation to success checkmark added for library import flow. ([#883](https://github.com/ashwkun/boxlore/pull/883)) <!-- impact:user-impact-low -->
### Changed
- Visual hierarchy and spacing adjusted for top‑level, child, and nested section headers; daypart rotation enabled via ordering and short‑lived retention of recently visible sections; network loading logic updated to preserve in‑flight section loads and prevent data clearing on empty refresh; Gradle wrapper upgraded and R8 compatibility enabled for release builds. ([#882](https://github.com/ashwkun/boxlore/pull/882)) <!-- impact:user-impact-high+backend-change -->
- Library import flow UI refined with clearer selector, progress, and success screens; cross‑promotion detection widened via additional title patterns, description cues, and improved match scoring; import UI extracted from MainActivity into ui.libraryimport package. ([#883](https://github.com/ashwkun/boxlore/pull/883)) <!-- impact:user-impact-low -->
- Documentation rewritten for recommendation‑system guide covering UI rails, on‑device bandit logic, API contracts, and diagnostics; obsolete recommendation_logic documentation removed. ([#880](https://github.com/ashwkun/boxlore/pull/880)) <!-- impact:no-user-impact -->
### Fixed
- Exclusion of placeholder Podcast genre from on‑device learner calculations fixed; blank gaps under greeting eliminated by painting cached rails immediately. ([#882](https://github.com/ashwkun/boxlore/pull/882)) <!-- impact:user-impact-high+backend-change -->

## [v0.0.9] - 2026-07-16

### Added
- RSS feed subscription with on-device HTTPS fetch (25 MB cap), Podcasting 2.0 parsing, new `rss_episodes` table and deterministic `rss:` IDs; Settings hub replaces Profile screen with six sub‑pages (Appearance, Playback, Downloads, Library, Privacy, About) plus custom HSV accent colour, three background styles, privacy disclosures, public website, reset‑analytics‑ID and deletion‑request flows; backup format v3 and OPML import/export restoring RSS feeds via URL re‑fetch. ([#874](https://github.com/ashwkun/boxlore/pull/874)) <!-- impact:user-impact-high -->
- Global intro/outro trimming with configurable durations and per‑podcast overrides preserving resume and completion semantics; customizable backward and forward seek increments configurable in settings and applied to player UI, notification controls, and Android Auto. ([#875](https://github.com/ashwkun/boxlore/pull/875)) <!-- impact:user-impact-high -->
- Adaptive content orchestration with on-device ranking foundation, versioned model storage, deterministic fallback for Home, discovery, queue, and downloads; isolation of adaptive state from library database and exclusion from device backup. ([#876](https://github.com/ashwkun/boxlore/pull/876)) <!-- impact:user-impact-high -->
- Material 3 Expressive episode details layout with artwork header, cohesive action controls, sticky playback rail component; visible buffering state UI in PlaybackController reflecting network buffering status. ([#877](https://github.com/ashwkun/boxlore/pull/877)) <!-- impact:user-impact-high -->
- In‑app announcement dialog extracted to `InAppAnnouncementDialog` with category layout presets; Play Store install suppression of What’s New / NEW RELEASE GitHub‑download prompts; release announce payloads now support up to five changelog bullets; PostHog events (viewed, dismissed, action) emitted for announcements. ([#870](https://github.com/ashwkun/boxlore/pull/870)) <!-- impact:user-impact-medium -->
### Changed
- Navigation, library and player UI migrated to Material 3 components (ShortNavigationBar, collapsing large titles, revised player sheet settle to eliminate overshoot and keep mini‑player controls responsive); region controls redesign – removal of nudge banners, addition of segmented selector in Settings and region chip on Explore charts header; toolchain and dependencies upgraded to AGP 8.10.1, Kotlin 2.4.0, KSP 2.3.10, Room 2.8.4, rss‑parser 6.1.7. ([#874](https://github.com/ashwkun/boxlore/pull/874)) <!-- impact:user-impact-high -->
- Preserved existing released API behavior as a fallback when adaptive ranking is unavailable. ([#876](https://github.com/ashwkun/boxlore/pull/876)) <!-- impact:user-impact-high -->
- Home feed hierarchy now incorporates adaptive recommendations via HomeFeedRepository, ordering content based on time-of-day; styling of notes, promotions, and recommendation sections in episode details UI updated to match new design. ([#877](https://github.com/ashwkun/boxlore/pull/877)) <!-- impact:user-impact-high -->
- Announcement dialog no longer dismisses on outside tap or system back; only top‑right X or Dismiss button clears it (primary CTA still clears after opening the route). ([#870](https://github.com/ashwkun/boxlore/pull/870)) <!-- impact:user-impact-medium -->
- CI pipeline automatically deletes orphan `release/v*` branches after successful publish via `delete_branch_on_merge` and `prepare_release.py delete‑release‑branch`. ([#871](https://github.com/ashwkun/boxlore/pull/871)) <!-- impact:no-user-impact -->

## [v0.0.8] - 2026-07-12

### Added
- Extended notification workflow with dry‑run mode, Admin UI support, collapse‑key input, sound routing options (chime, announcement, silent), HTML5 preview, live validation of route prefixes and image asset status, persisted notification templates in localStorage, workflow execution list view, updated compose dialog card layout in MainActivity.kt to Material 3 color tokens, custom action button labels, visibility controls, and dispatch readiness lock/unlock state banners. ([#861](https://github.com/ashwkun/boxlore/pull/861))
- Added customizable category badge label parameter to InAppAnnouncementDialog, configurable via FCM payload. ([#862](https://github.com/ashwkun/boxlore/pull/862))
- Added documentation describing the FCM notification fix and migration steps for affected users. ([#863](https://github.com/ashwkun/boxlore/pull/863))
- Implemented Android Auto integration via AutoConnectionManager service, introduced AutoBrowseNode hierarchy for browsing and AutoActionHandler for action handling. ([#865](https://github.com/ashwkun/boxlore/pull/865))
### Changed
- Refactored BoxLoreFcmService.showPushNotification into smaller private methods to reduce cognitive complexity and eliminate duplicate branches. ([#861](https://github.com/ashwkun/boxlore/pull/861))
- Constrained announcement dialog height with headroom and enabled scrolling for overflow content. ([#863](https://github.com/ashwkun/boxlore/pull/863))
### Fixed
- Stabilized artwork image transition in PlayerBottomSheet during drag gestures and updated next‑episode chevron styling and position to use Material 3 accent colors. ([#864](https://github.com/ashwkun/boxlore/pull/864))
- Fixed dark‑mode contrast calculation in Survey using Material surface background fallback, reconciled FCM topic subscriptions on reinstall/device migration by re‑subscribing notification‑enabled podcasts via sentinel file in noBackupFilesDir, and corrected markdown bullet list rendering in announcement dialogs. ([#863](https://github.com/ashwkun/boxlore/pull/863))
- Replaced PostHog survey icon with Campaign megaphone in SurveyIconProvider, fixed rating bar container layout overflow in RatingBarView, and corrected low‑contrast text colors in SurveyPromptView to meet accessibility guidelines. ([#865](https://github.com/ashwkun/boxlore/pull/865))

## [v0.0.7] - 2026-07-12

### Added
- Smart Queue v2: tiered SmartQueueEngine (T0–T4, T3.5) with batch refill, skip memory, region‑aware recommendations, signal‑aware Tier 3 routing, and unified guarded refill path in BoxLorePlaybackService; Queue UX enhancements include drag‑to‑reorder sheet, provenance source labels, undo‑remove snackbar with deferred skip signal, and Lore queue conflict dialog (LORE contextType). ([#853](https://github.com/ashwkun/boxlore/pull/853))
- Responsive Material 3 Expressive full player and polished mini player UI replacing legacy player sheet; playback utilities include inline and fullscreen transcripts, queue previews, show notes, chapters, persisted playback speed, and enhanced loading and motion states. ([#856](https://github.com/ashwkun/boxlore/pull/856))
- Adaptive palette‑driven halo background and layered swipe‑deck UI for Lore with in‑card action row and integrated playback rail; overflow‑aware content layout that hides podcast metadata strip on cards with overflow and presents themed queue‑conflict confirmation; redesigned share sheet offering Copy, Send, and Instagram Story destinations with artwork preview, timestamp support, and generation of square and 9:16 story cards using Material expressive shapes; centralized URL generation in ShareLinkBuilder and surface‑specific routing via ShareTarget with direct Instagram Story intent support and URI permission handling; reusable Lore visual primitives and Android‑canvas rendering support for expressive shapes. ([#858](https://github.com/ashwkun/boxlore/pull/858))
- Implemented loading of reduced‑size curiosity pages with automatic advancement past dismissed cards; introduced a caught‑up state including refresh and history recovery actions. ([#857](https://github.com/ashwkun/boxlore/pull/857))
### Changed
- Integrated SmartQueueEngine with Android Auto via unified refill guard; sleep‑timer now blocks end‑of‑episode refill; AUTO_FILL provenance persisted before append; contextSourceId gates Tier 0 on discovery landings. ([#853](https://github.com/ashwkun/boxlore/pull/853))
- Restored queue handling now deduplicates episodes on restore; click animation handling updated to avoid sticking during rapid input. ([#856](https://github.com/ashwkun/boxlore/pull/856))
- Updated all share entry points (full player, episode details, podcast details) to use new share infrastructure and visual primitives. ([#858](https://github.com/ashwkun/boxlore/pull/858))
- Replaced Lore screen branding assets with refreshed artwork. ([#857](https://github.com/ashwkun/boxlore/pull/857))
### Fixed
- Fixed Tier 0 newest‑sort guard for episodic/news and discovery landing skip; added Tier 3.5 deduplication when Tier 3 already similar; corrected skip‑memory filters and removed auto‑fill skips. ([#853](https://github.com/ashwkun/boxlore/pull/853))
- Fixed marquee overflow‑state feedback loop in expanded player that caused vibrating or double‑rendered metadata. ([#858](https://github.com/ashwkun/boxlore/pull/858))
- Optimized Home tab feed by flattening into lazy staggered‑grid items and pinning hero/Your Shows to reduce recomposition lag; cached skeleton shimmer draw paths and slowed animation to 2.2 s. ([#851](https://github.com/ashwkun/boxlore/pull/851))
### Removed
- Removed QueueManager.refillQueue() and PlaybackRepository.queueRefillCallback in favor of single service‑side refill. ([#853](https://github.com/ashwkun/boxlore/pull/853))
## [v0.0.6] - 2026-07-09
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
- Fixed skip-gate clock skew sensitivity and failure tracking in iTunes charts sync script.
- Fixed Qdrant orphan vectors, multiline CSV description split issues, missing cleanup run costs, and manual embedding budget overrides in the sync workflow.
- Fixed onboarding backup-restore recreation loop and region code alias normalization issues.
- Fixed Qdrant existence checks by chunking requests in batches of 1000 IDs to avoid oversized request payloads.
- Fixed double density scaling in image proxy URLs and stale proxyUrl memoization on density changes.
- Fixed download artwork cleanup race condition and premature deletion of shared podcast artwork.
- Fixed centering of podcast chip in daily curiosity card when title is long.
- Fixed pagination race condition in LearnViewModel when load/refresh is in-flight.
- Fixed unoptimized palette decodes, stale ambient accent colors, and unlocked play button clicks on loading.

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
