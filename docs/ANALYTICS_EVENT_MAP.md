# Boxlore analytics event map (from product insight)

Contract for engineering. Prefer few events + rich properties. `entry_point` mandatory on all `playback_*`.

## Replacement policy (locked)
- **Replace** legacy client events with this contract — hard cut in PR7 (A+B) and PR9 (C). No long-term dual-write.
- Canonical names + properties live in the **event glossary sheet** (`docs/ANALYTICS_EVENT_GLOSSARY.md` + `docs/analytics/event_glossary.csv`).
- Glossary column `replaces_legacy` maps old PostHog names for historical analysis only; new dashboards use new names only.
- Same event name may survive only if the **property schema** is upgraded to the glossary (e.g. `playback_started` + required props).

## Design principles
- Few events, rich properties; `action` discriminators for low-volume chrome
- `entry_point` on every playback event (normalize to glossary; expand beyond tiny `PlaybackEntryPoint` enum)
- IDs over titles for content; **no audio URLs / emails**
- **REQUIRED (locked):** log raw **AI chat user input text** and raw **search queries** to PostHog — do not hash or omit when the user typed something
- Person props for durable traits; events for behaviors; compose funnels in PostHog
- Keep hybrid progress model: percent milestones (10/25/50/75/90/100) + listening-time via pause/complete deltas
- Allowlist test: emitted event names ⊆ glossary for the shipping phase

## Phase A — company health (must ship / replaces legacy)
Person props: `first_seen_at`, `onboarding_status`, `onboarding_method`, `install_channel`, `notifications_enabled`, `initial_podcasts_subscribed`, `first_play_at`, `activated_at`

Events: `app_open`, `app_background`, `install_attributed`, `deep_link_opened`, onboarding suite including `onboarding_abandoned` / `onboarding_step_viewed`, **`onboarding_ai_turn_submitted` with required `user_input_text` (raw)**, **`onboarding_search_performed` / `search_performed` with required `search_query` (raw)**, `playback_started|heartbeat|paused|completed|error|buffering`, `session_restore_prompt`, `podcast_subscription_toggled`, notification permission + `notification_tapped` (+ `notification_received`), `identity_reset`, `app_check_status`, `first_episode_played`

## Phase B — discovery + library (replaces fragmented legacy)
`home_surface_tapped`, unified `search_performed` / `search_result_tapped`, Learn card family (consolidate or single `learn_card_action`), `queue_modified`, like/mark-played, download_requested/completed/failed, `smart_download_sync`, `show_notification_toggled`, share_*, `backup_restore_result`, `feedback_submitted`, library destination views

## Phase C — Auto + polish
`android_auto_connected|disconnected|browse`; derive ranking from actions; Learn caught-up/history; catalog_miss; rss_refresh_failed; progress_sync_anomaly

## Legacy handling (not “keep forever”)
- Inventory all current `PostHog.capture` / track* names in PR2 → fill `replaces_legacy`
- **PR7 (shipped):** deletes / remaps call sites for names not in glossary A∪B; façade allowlists via `AnalyticsGlossary` + `AnalyticsEmit`
- Historical PostHog: query old names via glossary mapping; do not keep emitting them
- **PR9:** Phase C events (Auto, chapters/transcripts, ranking status, proxy fallback, late-night)

## entry_point glossary
See EVENT_GLOSSARY enum sheet. Normalize legacy strings (`home_hero_resume_grid`, `resume_mini_player`, `episode_info_screen`, …) in one helper.

## Derived dashboards
DAU/WAU/MAU; W1/W4 retention; open→play; start→complete; hours/WAU; activation %; channel growth; subscribed vs discovery listening; entry-point mix; install→deeplink→play; Home first-tap→start→complete; notif→play — all tagged in glossary `dashboard_tags` / `funnel_roles`.
