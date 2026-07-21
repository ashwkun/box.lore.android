# Analytics event glossary

Canonical client analytics contract. Emit only listed `event_name` values.
Companion CSV: [`docs/analytics/event_glossary.csv`](analytics/event_glossary.csv).
Coverage inventory: [`docs/analytics/glossary_emission_coverage.csv`](analytics/glossary_emission_coverage.csv).
Architecture: [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## SDK-backed vs custom (no double-count)

| Glossary name | Production source of truth | App action |
| :--- | :--- | :--- |
| `app_open` | SDK **Application Opened** | Do **not** `capture("app_open")`. Keep `captureApplicationLifecycleEvents = true`. |
| `app_background` | SDK **Application Backgrounded** | Do **not** emit custom background. |
| Install volume (DNI/WNI/MNI / Weekly Pulse) | SDK **Application Installed** | Keep Weekly Pulse on Application Installed / Application Opened. |
| `install_attributed` | Person `$set_once` `install_channel` | Attribution only — **never** a second install-count event. |
| `deep_link_opened` | Custom glossary emit | SDK `captureDeepLinks = false`. |

Launch enrichment (subscription bucket, onboarding status) goes on **person properties**, not a second open event.

## Enums

### entry_point

`home_hero_resume`, `home_hero_jump_back_in`, `home_hero_new_episodes`, `home_hero_spotlight`, `home_mixtape`, `home_adaptive_*`, `home_because_you_like`, `home_discover_grid`, `home_recommendations`, `briefing`, `explore_for_you`, `explore_trending`, `explore_category`, `explore_search_shows`, `explore_search_episodes`, `learn`, `podcast_detail`, `episode_detail`, `queue`, `downloads`, `history`, `liked`, `notification`, `deep_link`, `share`, `install_referrer`, `android_auto`, `android_auto_continue`, `android_auto_queue`, `android_auto_new_episodes`, `android_auto_mixtape`, `android_auto_downloads`, `android_auto_liked`, `android_auto_history`, `android_auto_discover`, `android_auto_voice`, `android_auto_play_all`, `mini_player`, `session_restore`, `player_up_next`, `smart_queue`, `onboarding_suggestion`, `unknown`

### surface

`home`, `explore`, `learn`, `library_hub`, `library_subscriptions`, `library_liked`, `library_downloads`, `library_history`, `podcast_detail`, `episode_detail`, `player_full`, `player_mini`, `queue_sheet`, `settings`, `onboarding`, `briefing`, `android_auto`, `system_notification`, `share_sheet`

### content_type

`podcast`, `episode`, `briefing`, `mixtape`, `announcement`, `unknown`

### Other

- playback_mode: `stream` | `offline`
- client_surface: `phone` | `android_auto`
- install_channel: `play_store` | `apk_github` | `share_referrer` | `unknown`
- search_mode: `show_keyword` | `episode_semantic`
- pause_reason: `user_voluntary` | `interruption` | `error` | `sleep_timer` | `app_kill` | `auto_pause`
- subscription_count_bucket: `0` | `1_3` | `4_10` | `10_plus`
- daypart: `morning` | `afternoon` | `evening` | `late_night`

## Events

| event_name | meaning | required_properties | optional_properties | pii_risk |
| :--- | :--- | :--- | :--- | :--- |
| `app_open` | DAU volume via SDK **Application Opened** (glossary alias; not custom-emitted) | — (SDK) | person props for enrichment | none |
| `app_background` | Session end via SDK **Application Backgrounded** (not custom-emitted) | — (SDK) | — | none |
| `install_attributed` | Channel attribution only (`$set_once` install_channel); volume is **Application Installed** | install_channel on person | referrer_raw; utm_source; share_token | none |
| `deep_link_opened` | Inbound deep/share link handled | link_scheme:string; is_first_open:bool; cold_start:bool | link_host:string; content_type:enum; podcast_id:string; episode_id:string | none |
| `onboarding_started` | User entered onboarding | entry_point:string | is_reentry:bool | none |
| `onboarding_flow_selected` | User chose AI/genre/search/import | flow_type:string; entry_point:string | — | none |
| `onboarding_step_viewed` | A discrete onboarding step became visible | step_name:string; flow_type:string | step_index:int | none |
| `onboarding_abandoned` | User left before complete | last_step:string; flow_type:string | time_spent_seconds:int; subscribed_count:int | none |
| `onboarding_completed` | User finished an onboarding path | method:string; total_subscribed_count:int | screen:string; subscribed_podcasts_list:list; total_onboarding_time_seconds:float; entry_point:string; favorite_genres:list; did_scroll_suggestions:bool; did_switch_from_ai:bool; import_type:string; searches_performed:int | none |
| `onboarding_ai_turn_submitted` | User sent a message in onboarding AI chat | turn_number:int; user_input_text:string; has_custom_input:bool | selected_options:list; time_spent_seconds:float | logged_by_policy |
| `onboarding_ai_response_received` | Assistant reply returned in onboarding AI | turn_number:int; options_count:int | options_list:list; duration_seconds:float; detected_intent:string; assistant_message:string | none |
| `onboarding_ai_search_redirect` | AI suggested leaving chat for search | turn_number:int | suggested_query:string | logged_by_policy |
| `onboarding_ai_synthesis_completed` | AI produced podcast suggestions | rows_count:int; podcasts_count:int | duration_seconds:float | none |
| `onboarding_ai_synthesis_failed` | AI suggestion generation failed | — | error_message:string | none |
| `onboarding_search_performed` | Search during onboarding | search_query:string; results_count:int | query_length:int | logged_by_policy |
| `onboarding_search_podcast_subscribed` | Subscribed from onboarding search result | podcast_id:string; total_subscribed_count:int | podcast_name:string | none |
| `onboarding_import_sheet_opened` | OPML/import sheet shown | — | entry_point:string | none |
| `onboarding_import_failed` | OPML/library import failed | import_type:string | error_message:string | none |
| `onboarding_manual_step_completed` | Genre/manual onboarding step finished | step_name:string; selections_count:int | selections_list:list; time_spent_seconds:float | none |
| `playback_started` | Audio playback started | episode_id:string; podcast_id:string; entry_point:enum; is_resume:bool; playback_mode:enum; client_surface:enum; speed:float; is_subscribed:bool | position_seconds:float; duration_seconds:float | none |
| `playback_heartbeat` | Periodic progress while playing | episode_id:string; podcast_id:string; entry_point:enum; position_seconds:float; playback_mode:enum; client_surface:enum | duration_seconds:float; percent_complete:float; milestone:int; speed:float; is_subscribed:bool | none |
| `playback_paused` | Playback paused | episode_id:string; podcast_id:string; entry_point:enum; position_seconds:float; listened_delta_seconds:float; pause_reason:enum; playback_mode:enum; client_surface:enum | duration_seconds:float; percent_complete:float; is_subscribed:bool | none |
| `playback_completed` | Episode reached completion | episode_id:string; podcast_id:string; entry_point:enum; listened_delta_seconds:float; playback_mode:enum; client_surface:enum | duration_seconds:float; is_subscribed:bool; speed:float | none |
| `playback_error` | Playback failed | error_type:string; entry_point:enum | episode_id:string; podcast_id:string; error_message:string; playback_mode:enum; client_surface:enum | none |
| `playback_buffering` | Buffering stall observed | entry_point:enum | episode_id:string; podcast_id:string; buffer_duration_ms:int; playback_mode:enum; client_surface:enum | none |
| `playback_seeked` | User seeked in episode | episode_id:string; podcast_id:string; entry_point:enum; from_seconds:float; to_seconds:float | seek_source:string; client_surface:enum | none |
| `session_restore_prompt` | Resume previous session prompt shown/acted | action:string | episode_id:string; podcast_id:string; position_seconds:float | none |
| `podcast_subscription_toggled` | User subscribed or unsubscribed | podcast_id:string; is_subscribed:bool | surface:string; source:string | none |
| `notification_permission_requested` | System permission prompt requested | — | surface:string | none |
| `notification_permission_decided` | User granted/denied notifications | granted:bool | — | none |
| `notification_tapped` | User opened a push/local notification | notification_type:string | podcast_id:string; episode_id:string; target_route:string | none |
| `notification_received` | Push/local notification delivered | notification_type:string | podcast_id:string; episode_id:string | none |
| `identity_reset` | Analytics identity cleared/reset | — | reason:string | none |
| `app_check_status` | App Check token attempt result | token_obtained:bool; provider:string | — | none |
| `first_episode_played` | First-ever play milestone | — | episode_id:string; podcast_id:string; entry_point:enum; hours_since_install:float | none |
| `home_surface_tapped` | First-class Home component tap | surface_component:string | rail_intent:string; content_id:string; position_index:int; feedback_action:string; personalization_mode:string; exposure_id:string | none |
| `home_surface_impression` | Home rail/block became visible | surface_component:string | items_count:int; personalization_mode:string; meaningful_play_count:int; is_fallback:bool; candidate_request_ok:bool | none |
| `search_performed` | User ran show or episode search | surface:string; search_mode:enum; search_query:string; results_count:int | result_quality:enum; query_length:int | logged_by_policy |
| `search_result_tapped` | User tapped a search result | surface:string; result_type:enum | search_mode:enum; podcast_id:string; episode_id:string; position_index:int; search_query:string | logged_by_policy |
| `learn_card_action` | User acted on a Learn card | action:string; episode_id:string; podcast_id:string | position_index:int | none |
| `learn_screen_viewed` | Learn tab/screen opened | — | cards_available:int | none |
| `explore_screen_viewed` | Explore opened | — | tab:string | none |
| `explore_recommendation_tapped` | For You/trending card tapped | — | podcast_id:string; position_index:int; rail:string | none |
| `queue_modified` | Queue add/remove/reorder/clear | action:string | episode_id:string; podcast_id:string; queue_size:int; source:string | none |
| `episode_liked_toggled` | Like/unlike episode | episode_id:string; podcast_id:string; is_liked:bool | surface:string | none |
| `episode_mark_played` | User marked episode played/unplayed | episode_id:string; podcast_id:string; is_played:bool | surface:string | none |
| `download_requested` | User or Smart Downloads requested download | episode_id:string; podcast_id:string; source:string | wifi_only:bool | none |
| `download_completed` | Episode finished downloading | episode_id:string; podcast_id:string | source:string; bytes:long; duration_ms:long | none |
| `download_failed` | Download failed | error_type:string | episode_id:string; podcast_id:string; source:string; error_message:string | none |
| `smart_download_sync` | Smart Downloads sync ran | — | requested_count:int; completed_count:int; failed_count:int; cleaned_count:int; trigger:string | none |
| `show_notification_toggled` | Per-show notification preference changed | podcast_id:string; enabled:bool | — | none |
| `share_content` | User shared podcast/episode/app | content_type:enum | podcast_id:string; episode_id:string; channel:string; surface:string | none |
| `backup_restore_result` | Library backup or restore finished | action:string; success:bool | item_count:int; format:string; error_message:string | none |
| `feedback_submitted` | In-app feedback/NPS/review handoff | feedback_type:string | score:int; source:string | none |
| `library_destination_viewed` | Library hub or sub-destination opened | destination:string | item_count:int | none |
| `podcast_detail_viewed` | Podcast info screen opened | podcast_id:string | is_subscribed:bool | none |
| `episode_detail_viewed` | Episode info screen opened | episode_id:string; podcast_id:string | — | none |
| `nav_tab_clicked` | Bottom nav tab selected | tab:string | previous_tab:string | none |
| `settings_interaction` | Settings screen view or control | action:string | setting_key:string | none |
| `feature_announcement_action` | In-app/feature announcement viewed/dismissed/acted | action:string | feature_id:string; category:string | none |
| `offline_mode_entered` | App detected offline / offline UI | — | reason:string | none |
| `player_chrome_interaction` | Mini/full player or control bar action | surface:string; action:string | — | none |
| `daily_briefing_action` | Briefing impression or interaction | action:string; region:string; date:string | content_id:string; source:string; chapter_index:int; chapter_title:string; method:string; playback_status:string; previous_region:string; episode_id:string; episode_title:string; podcast_id:string; podcast_title:string | none |
| `home_import_banner_action` | Import banner shown/clicked/dismissed | action:string | — | none |
| `library_history_tracking_notice` | One-time listening-history tracking reset notice shown/dismissed | action:string | — | none |
| `android_auto_connected` | Auto session began | — | session_id:string | none |
| `android_auto_disconnected` | Auto session ended | — | session_id:string; duration_seconds:int | none |
| `android_auto_browse` | User browsed Auto media tree | node:string | action:string | none |
| `adaptive_ranking_status` | Ranking engine status/update | status:string | details:string | none |
| `learn_caught_up` | User reached Learn caught-up state | — | cards_remaining:int | none |
| `catalog_miss` | Catalog/lookup miss | lookup_type:string | key:string | none |
| `rss_refresh_failed` | RSS feed refresh failed | — | podcast_id:string; error_type:string | none |
| `progress_sync_anomaly` | Progress sync inconsistency detected | anomaly_type:string | episode_id:string | none |
| `late_night_safeguard_decision` | Late-night listening safeguard fired | decision:string | — | none |
| `auto_chapters_lifecycle` | Chapters request/complete/fail | stage:string | episode_id:string; error_message:string | none |
| `auto_transcript_lifecycle` | Transcript request/complete/fail | stage:string | episode_id:string; error_message:string | none |
| `proxy_fallback_triggered` | Image load fell back to proxy | — | reason:string | none |

## Person properties

| property | set_when | notes |
| :--- | :--- | :--- |
| first_seen_at | first process | `$set_once` |
| onboarding_status | pending / completed / skipped_deeplink | |
| onboarding_method | ai_chat / manual_genre / search / import / skip / deeplink_skip | |
| install_channel | first open | |
| notifications_enabled | permission decide | |
| initial_podcasts_subscribed | onboarding complete | |
| first_play_at | first playback_started | `$set_once` |
| activated_at | onboarding complete + play ≤24h | `$set_once` |

## Logging policy

- Log `user_input_text` on AI chat turns and `search_query` on search events (`pii_risk=logged_by_policy`).
- Before emission, scrub those fields: remove email addresses, password-like tokens, and any `http(s)` audio/enclosure/media URLs. Keep the remaining free-text query/message.
- Never emit emails, passwords, or audio/enclosure URLs as standalone property values.
- `entry_point` is required on all `playback_*` events.
