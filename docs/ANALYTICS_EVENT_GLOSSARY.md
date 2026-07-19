# Boxlore — Analytics event glossary (sheet)

**Purpose:** Single sheet for product analysis and PostHog dashboard setup.
**Authority:** Code may only emit `event_name` values listed here for the shipping phase.
**Policy:** Replaces legacy client events (hard cut). Use `replaces_legacy` only for historical PostHog queries.

Companion CSV (same columns): [`docs/analytics/event_glossary.csv`](analytics/event_glossary.csv).

**PR9 status:** Phase C Auto + polish **shipped** — façade emits glossary A∪B∪C; allowlist includes Phase C. Auto connect/disconnect/browse wired from `AutoBrowseLibraryCallback`.

---

## Column definitions

| Column | Meaning |
|:--|:--|
| event_name | Canonical snake_case emitted to PostHog |
| display_name | Short label on dashboards |
| description | When it fires (one sentence) |
| phase | A (company health) / B (discovery+library) / C (Auto+polish) |
| product_questions | Must/Nice questions from ANALYTICS_PRODUCT_INSIGHT |
| properties | `name:type:required` (semicolon-separated; Y/N required) |
| replaces_legacy | Old event names this supersedes (comma-separated) |
| dashboard_tags | e.g. company_health, activation, playback, entry_point, discovery, auto |
| funnel_roles | e.g. play_start, play_complete, activation_complete |
| pii_risk | none / logged_by_policy / forbidden |
| notes | Breakdown tips, person-prop pairing |

---

## Enum sheets (allowed values)

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

---

## Event rows

| event_name | display_name | description | phase | product_questions | properties | replaces_legacy | dashboard_tags | funnel_roles | pii_risk | notes |
|:--|:--|:--|:--|:--|:--|:--|:--|:--|:--|:--|
| app_open | App opened | Process/UI foreground open counted for DAU | A | DAU/WAU/MAU; habit; opens that start play | is_first_open:bool:Y;days_since_install:int:N;cold_start:bool:N;subscription_count_bucket:enum:Y;onboarding_status:string:Y;install_channel:enum:N |  | company_health;retention | session_start | none | Pair with person first_seen_at; new in glossary (was implicit) |
| app_background | App backgrounded | App moved to background | A | Session length; habit | session_duration_seconds:int:N;had_playback:bool:N;subscription_count_bucket:enum:N |  | company_health;retention | session_end | none | Derive sessions with app_open |
| install_attributed | Install attributed | Install/referrer channel resolved | A | Growth WoW; channel mix | install_channel:enum:Y;referrer_raw:string:N;utm_source:string:N;share_token:string:N |  | growth;company_health | install | none | $set_once install_channel person prop |
| deep_link_opened | Deep link opened | Inbound deep/share link handled | A | Share/referrer→open→play; deeplink first-open | link_scheme:string:Y;link_host:string:N;content_type:enum:N;podcast_id:string:N;episode_id:string:N;is_first_open:bool:Y;cold_start:bool:Y |  | growth;activation | deeplink_open | none | Keep boxlore+boxcast schemes |
| onboarding_started | Onboarding started | User entered onboarding | A | Start/complete rates; path mix | entry_point:string:Y;is_reentry:bool:N | onboarding_started | activation;onboarding | activation_start | none |  |
| onboarding_flow_selected | Onboarding flow selected | User chose AI/genre/search/import | A | Path mix × activation | flow_type:string:Y;entry_point:string:Y | onboarding_flow_selected | activation;onboarding | activation_path | none | flow_type: ai_chat\|manual_genre\|search\|import |
| onboarding_step_viewed | Onboarding step viewed | A discrete onboarding step became visible | A | Abandon step | step_name:string:Y;flow_type:string:Y;step_index:int:N | onboarding_manual_step_completed | activation;onboarding | activation_step | none | Replaces fragmented step completed-as-view misuse where applicable |
| onboarding_abandoned | Onboarding abandoned | User left before complete | A | Abandon step; activation | last_step:string:Y;flow_type:string:Y;time_spent_seconds:int:N;subscribed_count:int:N |  | activation;onboarding | activation_drop | none | Do not fold skip into onboarding_completed |
| onboarding_completed | Onboarding completed | User finished an onboarding path | A | Activation; path mix; initial subs | method:string:Y;screen:string:N;total_subscribed_count:int:Y;subscribed_podcasts_list:list:N;total_onboarding_time_seconds:float:N;entry_point:string:N;favorite_genres:list:N;did_scroll_suggestions:bool:N;did_switch_from_ai:bool:N;import_type:string:N;searches_performed:int:N | onboarding_completed | activation;onboarding | activation_complete | none | method: ai_suggestions_done\|manual_genre_flow\|search_done\|import\|skip_welcome — skip is completion of skip path not abandon |
| onboarding_ai_turn_submitted | AI chat turn submitted | User sent a message in onboarding AI chat | A | AI path quality; what users say | turn_number:int:Y;user_input_text:string:Y;has_custom_input:bool:Y;selected_options:list:N;time_spent_seconds:float:N | onboarding_ai_turn_submitted | activation;onboarding;ai | ai_turn | logged_by_policy | ALWAYS log raw user_input_text (rename from custom_input_text) |
| onboarding_ai_response_received | AI response received | Assistant reply returned in onboarding AI | A | AI path quality | turn_number:int:Y;options_count:int:Y;options_list:list:N;duration_seconds:float:N;detected_intent:string:N;assistant_message:string:N | onboarding_ai_response_received | activation;onboarding;ai |  | none | assistant_message optional truncated |
| onboarding_ai_search_redirect | AI search redirect | AI suggested leaving chat for search | A | Search redirect follow-through | turn_number:int:Y;suggested_query:string:N | onboarding_ai_search_redirect | activation;onboarding;ai |  | logged_by_policy | suggested_query raw when present |
| onboarding_ai_synthesis_completed | AI synthesis completed | AI produced podcast suggestions | A | AI path → subscribe | rows_count:int:Y;podcasts_count:int:Y;duration_seconds:float:N | onboarding_ai_synthesis_completed | activation;onboarding;ai |  | none |  |
| onboarding_ai_synthesis_failed | AI synthesis failed | AI suggestion generation failed | A | AI path drop | error_message:string:N | onboarding_ai_synthesis_failed | activation;onboarding;ai |  | none | No PII in errors |
| onboarding_search_performed | Onboarding search performed | Search during onboarding | A | Onboarding search→subscribe | search_query:string:Y;results_count:int:Y;query_length:int:N | onboarding_search_performed | activation;onboarding;search | search | logged_by_policy | ALWAYS log raw search_query; also emit unified search_performed in PR7 |
| onboarding_search_podcast_subscribed | Onboarding search subscribe | Subscribed from onboarding search result | A | Search→subscribe | podcast_id:string:Y;podcast_name:string:N;total_subscribed_count:int:Y | onboarding_search_podcast_subscribed | activation;onboarding |  | none | IDs preferred |
| onboarding_import_sheet_opened | Import sheet opened | OPML/import sheet shown | A | OPML path | entry_point:string:N | onboarding_import_sheet_opened | activation;onboarding |  | none |  |
| onboarding_import_failed | Import failed | OPML/library import failed | A | OPML parse success | import_type:string:Y;error_message:string:N | onboarding_import_failed | activation;onboarding |  | none |  |
| onboarding_manual_step_completed | Manual step completed | Genre/manual onboarding step finished | A | Manual path steps | step_name:string:Y;selections_count:int:Y;selections_list:list:N;time_spent_seconds:float:N | onboarding_manual_step_completed | activation;onboarding | activation_step | none |  |
| playback_started | Playback started | Audio playback started | A | Entry-point mix; open→play; activation | episode_id:string:Y;podcast_id:string:Y;entry_point:enum:Y;is_resume:bool:Y;playback_mode:enum:Y;client_surface:enum:Y;speed:float:Y;is_subscribed:bool:Y;position_seconds:float:N;duration_seconds:float:N | playback_started | playback;entry_point;activation;company_health | play_start | none | entry_point REQUIRED; schema replace |
| playback_heartbeat | Playback heartbeat | Periodic progress while playing | A | Listening hours; milestones | episode_id:string:Y;podcast_id:string:Y;entry_point:enum:Y;position_seconds:float:Y;duration_seconds:float:N;percent_complete:float:N;milestone:int:N;playback_mode:enum:Y;client_surface:enum:Y;speed:float:N;is_subscribed:bool:N | playback_heartbeat | playback;entry_point | play_progress | none | milestones 10/25/50/75/90/100 |
| playback_paused | Playback paused | Playback paused | A | Abandon timing; hours/WAU | episode_id:string:Y;podcast_id:string:Y;entry_point:enum:Y;position_seconds:float:Y;duration_seconds:float:N;percent_complete:float:N;listened_delta_seconds:float:Y;pause_reason:enum:Y;playback_mode:enum:Y;client_surface:enum:Y;is_subscribed:bool:N | playback_paused | playback;entry_point | play_pause | none |  |
| playback_completed | Playback completed | Episode reached completion | A | Start→complete; hours | episode_id:string:Y;podcast_id:string:Y;entry_point:enum:Y;listened_delta_seconds:float:Y;duration_seconds:float:N;playback_mode:enum:Y;client_surface:enum:Y;is_subscribed:bool:N;speed:float:N | playback_completed | playback;entry_point;company_health | play_complete | none |  |
| playback_error | Playback error | Playback failed | A | Play fail rate | episode_id:string:N;podcast_id:string:N;entry_point:enum:N;error_type:string:Y;error_message:string:N;playback_mode:enum:N;client_surface:enum:N | playback_error | playback | play_error | none | No audio URLs |
| playback_buffering | Playback buffering | Buffering stall observed | A | Delight killers; stream quality | episode_id:string:N;podcast_id:string:N;entry_point:enum:N;buffer_duration_ms:int:N;playback_mode:enum:N;client_surface:enum:N |  | playback |  | none | New; was missing |
| playback_seeked | Playback seeked | User seeked in episode | A | Early skip as rec signal | episode_id:string:Y;podcast_id:string:Y;entry_point:enum:Y;from_seconds:float:Y;to_seconds:float:Y;seek_source:string:N;client_surface:enum:N | playback_seeked | playback;entry_point |  | none |  |
| session_restore_prompt | Session restore prompt | Resume previous session prompt shown/acted | A | Session restore play vs dismiss | action:string:Y;episode_id:string:N;podcast_id:string:N;position_seconds:float:N |  | playback;activation |  | none | action: shown\|play\|dismiss |
| podcast_subscription_toggled | Subscription toggled | User subscribed or unsubscribed | A | Sub count; time-to-first-play after sub | podcast_id:string:Y;is_subscribed:bool:Y;surface:string:N;source:string:N | podcast_subscription_toggled | library;activation | subscribe | none |  |
| notification_permission_requested | Notification permission requested | System permission prompt requested | A | Notification loop vs trust | surface:string:N | notification_permission_requested | notifications;activation |  | none |  |
| notification_permission_decided | Notification permission decided | User granted/denied notifications | A | Notification enable rate | granted:bool:Y | notification_permission_decided | notifications;activation |  | none | Also set person notifications_enabled |
| notification_tapped | Notification tapped | User opened a push/local notification | A | Notif→play | notification_type:string:Y;podcast_id:string:N;episode_id:string:N;target_route:string:N | notification_tapped | notifications;growth | notif_open | none |  |
| notification_received | Notification received | Push/local notification delivered | A | Notif delivery vs tap | notification_type:string:Y;podcast_id:string:N;episode_id:string:N |  | notifications |  | none | New; may be FCM-side |
| identity_reset | Identity reset | Analytics identity cleared/reset | A | Privacy; identity hygiene | reason:string:N |  | company_health |  | none | New explicit event |
| app_check_status | App Check status | App Check token attempt result | A | Integrity / abuse | token_obtained:bool:Y;provider:string:Y | app_check_status | company_health |  | none |  |
| first_episode_played | First episode played | First-ever play milestone | A | Activation; time-to-first-play | episode_id:string:N;podcast_id:string:N;entry_point:enum:N;hours_since_install:float:N | first_episode_played | activation;company_health | first_play | none | $set_once first_play_at |
| home_surface_tapped | Home surface tapped | First-class Home component tap | B | Home first tap; surface→play | surface_component:string:Y;rail_intent:string:N;content_id:string:N;position_index:int:N | home_recommendation_card_tapped;home_hero_carousel_swiped;home_podcast_filtered;play_mix_clicked;daily_briefing_banner_tapped;daily_briefing_play_clicked;curated_card_tapped;home_import_banner_clicked | discovery;home | discovery_tap | none | Consolidates fragmented home_* taps |
| home_surface_impression | Home surface impression | Home rail/block became visible | B | Home reach | surface_component:string:Y;items_count:int:N | home_recommendations_impression;curated_block_impression;daily_briefing_card_impression;home_import_banner_impression | discovery;home |  | none |  |
| search_performed | Search performed | User ran show or episode search | A/B | Query quality; zero results; Explore vs onboarding | surface:string:Y;search_mode:enum:Y;search_query:string:Y;results_count:int:Y;result_quality:enum:N;query_length:int:N | explore_search_performed;onboarding_search_performed | discovery;search | search | logged_by_policy | ALWAYS log raw search_query |
| search_result_tapped | Search result tapped | User tapped a search result | B | Search→play/subscribe | surface:string:Y;search_mode:enum:N;result_type:enum:Y;podcast_id:string:N;episode_id:string:N;position_index:int:N;search_query:string:N |  | discovery;search |  | logged_by_policy | search_query optional echo |
| learn_card_action | Learn card action | User acted on a Learn card | B | Learn session→play/queue; dismiss vs play | action:string:Y;episode_id:string:Y;podcast_id:string:Y;position_index:int:N | learn_card_dismissed;learn_card_queued;learn_card_info_clicked;learn_card_play_clicked;learn_card_podcast_clicked | discovery;learn |  | none | action: play\|queue\|info\|dismiss\|podcast |
| learn_screen_viewed | Learn screen viewed | Learn tab/screen opened | B | Learn reach | cards_available:int:N | learn_screen_viewed;learn_screen_session | discovery;learn |  | none | Session props optional |
| explore_screen_viewed | Explore screen viewed | Explore opened | B | Explore reach | tab:string:N | explore_screen_viewed;explore_screen_session | discovery;explore |  | none |  |
| explore_recommendation_tapped | Explore recommendation tapped | For You/trending card tapped | B | Explore→play | podcast_id:string:N;position_index:int:N;rail:string:N | explore_recommendation_card_tapped;explore_recommendations_impression;discover_category_filtered | discovery;explore |  | none |  |
| queue_modified | Queue modified | Queue add/remove/reorder/clear | B | Intentional queue use | action:string:Y;episode_id:string:N;podcast_id:string:N;queue_size:int:N;source:string:N | queue_reordered;smart_queue_episode_skipped;smart_queue_refilled;lore_queue_conflict_shown;lore_queue_conflict_result | library;queue |  | none | action: add\|remove\|reorder\|clear\|skip\|refill\|conflict_* |
| episode_liked_toggled | Episode liked toggled | Like/unlike episode | B | Liked replay | episode_id:string:Y;podcast_id:string:Y;is_liked:bool:Y;surface:string:N |  | library |  | none | New consolidated |
| episode_mark_played | Episode mark played | User marked episode played/unplayed | B | Library hygiene | episode_id:string:Y;podcast_id:string:Y;is_played:bool:Y;surface:string:N |  | library |  | none | New; was action-only in session aggregator |
| download_requested | Download requested | User or Smart Downloads requested download | B | Downloads as habit | episode_id:string:Y;podcast_id:string:Y;source:string:Y;wifi_only:bool:N |  | downloads | download_start | none | source: manual\|smart\|auto_show |
| download_completed | Download completed | Episode finished downloading | B | Download success | episode_id:string:Y;podcast_id:string:Y;source:string:N;bytes:long:N;duration_ms:long:N | download_completed | downloads | download_success | none |  |
| download_failed | Download failed | Download failed | B | Fail reasons | episode_id:string:N;podcast_id:string:N;source:string:N;error_type:string:Y;error_message:string:N | download_failed | downloads | download_fail | none | Enrich schema vs thin legacy |
| smart_download_sync | Smart download sync | Smart Downloads sync ran | B | Smart Downloads habit vs waste | requested_count:int:N;completed_count:int:N;failed_count:int:N;cleaned_count:int:N;trigger:string:N |  | downloads |  | none | New |
| show_notification_toggled | Show notification toggled | Per-show notification preference changed | B | Notif % among subs | podcast_id:string:Y;enabled:bool:Y |  | notifications;library |  | none | New |
| share_content | Share content | User shared podcast/episode/app | B | Share/deeplink growth | content_type:enum:Y;podcast_id:string:N;episode_id:string:N;channel:string:N;surface:string:N |  | growth;share | share | none | New consolidated share_* |
| backup_restore_result | Backup/restore result | Library backup or restore finished | B | Import/migrate retention | action:string:Y;success:bool:Y;item_count:int:N;format:string:N;error_message:string:N |  | library |  | none | action: backup\|restore\|opml_export\|opml_import |
| feedback_submitted | Feedback submitted | In-app feedback/NPS/review handoff | B | Promoters; delight | feedback_type:string:Y;score:int:N;source:string:N | survey_nps_eligible;survey_nps_manual_trigger;engagement_prompt_shown;promoter_review_handoff;feedback_submitted | company_health;delight |  | none | Consolidates NPS/engagement family where possible |
| library_destination_viewed | Library destination viewed | Library hub or sub-destination opened | B | Library destinations that drive listening | destination:string:Y;item_count:int:N | library_hub_viewed;library_hub_session;library_subscriptions_viewed;library_subscriptions_session;library_liked_viewed;library_liked_session;library_downloads_viewed;library_downloads_session;library_history_viewed;library_history_session;library_subscriptions_sort_changed;library_subscriptions_layout_toggled;library_subscriptions_genre_filtered | library |  | none | destination: hub\|subscriptions\|liked\|downloads\|history |
| podcast_detail_viewed | Podcast detail viewed | Podcast info screen opened | B | Visit→subscribe/play | podcast_id:string:Y;is_subscribed:bool:N | podcast_info_screen_viewed;podcast_info_screen_session | discovery;detail |  | none |  |
| episode_detail_viewed | Episode detail viewed | Episode info screen opened | B | Episode action mix | episode_id:string:Y;podcast_id:string:Y | episode_info_screen_viewed;episode_info_screen_session | discovery;detail |  | none |  |
| nav_tab_clicked | Nav tab clicked | Bottom nav tab selected | B | Tab bounce without play | tab:string:Y;previous_tab:string:N | nav_tab_clicked | discovery |  | none |  |
| settings_interaction | Settings interaction | Settings screen view or control | B | Settings discovery | action:string:Y;setting_key:string:N | settings_screen_viewed;settings_interaction | settings |  | none |  |
| feature_announcement_action | Feature announcement action | In-app/feature announcement viewed/dismissed/acted | B | Announcement efficacy | action:string:Y;feature_id:string:N;category:string:N | feature_announcement_viewed;feature_announcement_dismissed;in_app_announcement_viewed;in_app_announcement_dismissed;in_app_announcement_action | delight |  | none |  |
| offline_mode_entered | Offline mode entered | App detected offline / offline UI | B | Offline vs stream | reason:string:N | offline_mode_entered | downloads;playback |  | none |  |
| player_chrome_interaction | Player chrome interaction | Mini/full player or control bar action | B | Player UX | surface:string:Y;action:string:Y | mini_player_interaction;top_controlbar_interaction;full_player_screen_session | playback |  | none |  |
| daily_briefing_action | Daily briefing action | Briefing impression or interaction | B | Briefing→play | action:string:Y;region:string:N;content_id:string:N | daily_briefing_screen_viewed;daily_briefing_interaction;daily_briefing_pause_clicked;daily_briefing_region_changed;daily_briefing_related_episode_clicked;daily_briefing_card_impression;daily_briefing_banner_tapped;daily_briefing_play_clicked | discovery;briefing |  | none | home_surface_tapped also covers primary play CTA |
| home_import_banner_action | Home import banner action | Import banner shown/clicked/dismissed | B | Import from Home | action:string:Y | home_import_banner_impression;home_import_banner_clicked;home_import_banner_dismissed | activation;home |  | none |  |
| android_auto_connected | Android Auto connected | Auto session began | C | Auto share of listening | session_id:string:N |  | auto | auto_session | none | Shipped PR9 |
| android_auto_disconnected | Android Auto disconnected | Auto session ended | C | Auto session length | session_id:string:N;duration_seconds:int:N |  | auto | auto_session | none | Shipped PR9 |
| android_auto_browse | Android Auto browse | User browsed Auto media tree | C | Auto browse→play | node:string:Y;action:string:N |  | auto |  | none | Shipped PR9 |
| adaptive_ranking_status | Adaptive ranking status | Ranking engine status/update | C | Adaptive vs Discover | status:string:Y;details:string:N | adaptive_ranking_status | discovery;ranking |  | none | Derive more from actions in PR9 |
| learn_caught_up | Learn caught up | User reached Learn caught-up state | C | Learn return | cards_remaining:int:N |  | learn |  | none | Ship PR9 |
| catalog_miss | Catalog miss | Catalog/lookup miss | C | Catalog quality | lookup_type:string:Y;key:string:N |  | catalog |  | none | Ship PR9 |
| rss_refresh_failed | RSS refresh failed | RSS feed refresh failed | C | RSS reliability | podcast_id:string:N;error_type:string:N |  | catalog;rss |  | none | Ship PR9 |
| progress_sync_anomaly | Progress sync anomaly | Progress sync inconsistency detected | C | Sync trust | anomaly_type:string:Y;episode_id:string:N |  | playback |  | none | Ship PR9 |
| late_night_safeguard_decision | Late-night safeguard decision | Late-night listening safeguard fired | C | Late-night UX | decision:string:Y | late_night_safeguard_decision | playback;delight |  | none |  |
| auto_chapters_lifecycle | Auto chapters lifecycle | Chapters request/complete/fail | C | Chapters quality | stage:string:Y;episode_id:string:N;error_message:string:N | auto_chapters_requested;auto_chapters_completed;auto_chapters_failed | playback |  | none |  |
| auto_transcript_lifecycle | Auto transcript lifecycle | Transcript request/complete/fail | C | Transcript quality | stage:string:Y;episode_id:string:N;error_message:string:N | auto_transcript_requested;auto_transcript_completed;auto_transcript_failed | playback |  | none |  |
| proxy_fallback_triggered | Image proxy fallback | Image load fell back to proxy | C | CDN/proxy health | reason:string:N | proxy_fallback_triggered | infra |  | none | Emitted from designsystem today — move via façade PR6/PR7 |

---

## Legacy inventory (PR2 grep)

All current `PostHog.capture` event names found in-tree (must map via `replaces_legacy` or be deleted in PR7/PR9):

adaptive_ranking_status, app_check_status, auto_chapters_completed, auto_chapters_failed, auto_chapters_requested, auto_transcript_completed, auto_transcript_failed, auto_transcript_requested, curated_block_impression, curated_card_tapped, daily_briefing_banner_tapped, daily_briefing_card_impression, daily_briefing_interaction, daily_briefing_pause_clicked, daily_briefing_play_clicked, daily_briefing_region_changed, daily_briefing_related_episode_clicked, daily_briefing_screen_viewed, discover_category_filtered, download_completed, download_failed, engagement_prompt_shown, episode_info_screen_session, episode_info_screen_viewed, explore_recommendation_card_tapped, explore_recommendations_impression, explore_screen_session, explore_screen_viewed, explore_search_performed, feature_announcement_dismissed, feature_announcement_viewed, first_episode_played, full_player_screen_session, home_hero_carousel_swiped, home_import_banner_clicked, home_import_banner_dismissed, home_import_banner_impression, home_podcast_filtered, home_recommendation_card_tapped, home_recommendations_impression, in_app_announcement_action, in_app_announcement_dismissed, in_app_announcement_viewed, late_night_safeguard_decision, learn_card_dismissed, learn_card_info_clicked, learn_card_play_clicked, learn_card_podcast_clicked, learn_card_queued, learn_screen_session, learn_screen_viewed, library_downloads_session, library_downloads_viewed, library_history_session, library_history_viewed, library_hub_session, library_hub_viewed, library_liked_session, library_liked_viewed, library_subscriptions_genre_filtered, library_subscriptions_layout_toggled, library_subscriptions_session, library_subscriptions_sort_changed, library_subscriptions_viewed, lore_queue_conflict_result, lore_queue_conflict_shown, mini_player_interaction, nav_tab_clicked, notification_permission_decided, notification_permission_requested, notification_tapped, offline_mode_entered, onboarding_ai_response_received, onboarding_ai_search_redirect, onboarding_ai_synthesis_completed, onboarding_ai_synthesis_failed, onboarding_ai_turn_submitted, onboarding_completed, onboarding_flow_selected, onboarding_import_failed, onboarding_import_sheet_opened, onboarding_manual_step_completed, onboarding_search_performed, onboarding_search_podcast_subscribed, onboarding_started, play_mix_clicked, playback_completed, playback_error, playback_heartbeat, playback_paused, playback_seeked, playback_started, podcast_info_screen_session, podcast_info_screen_viewed, podcast_subscription_toggled, promoter_review_handoff, proxy_fallback_triggered, queue_reordered, settings_interaction, settings_screen_viewed, smart_queue_episode_skipped, smart_queue_refilled, survey_nps_eligible, survey_nps_manual_trigger, top_controlbar_interaction

Also emitted via dynamic `trackLearnCardEvent(...)`: `learn_card_dismissed`, `learn_card_queued`, `learn_card_info_clicked`, `learn_card_play_clicked`.

---

## User text logging (locked)

**Very important:** PostHog must receive:

1. **AI chat** — raw `user_input_text` on every user turn (`onboarding_ai_turn_submitted` and any later in-app AI chat events).
2. **Search** — raw `search_query` on every `search_performed` / onboarding search.

Do not hash, bucket-only, or omit these when the user typed something. Mark `pii_risk=logged_by_policy`. Still forbid emails, passwords, and audio/enclosure URLs.

---

## PostHog dashboard checklist (from tags)

Rebuild these on **new** event names after PR7 (document links in PR description; no CI click-through required):

1. **company_health** — DAU/WAU/MAU from `app_open`; hours from playback pause/complete
2. **activation** — onboarding → `activated_at` / first `playback_started` within 24h
3. **entry_point** — breakdown of `playback_started.entry_point` (retained cohort); note normalizer aliases (`home_hero_resume_grid` → `home_hero_resume`, `resume_mini_player` → `mini_player`, `episode_info_screen` → `episode_detail`)
4. **playback** — start → milestone → complete; buffering/errors; require `entry_point` on all `playback_*`
5. **discovery** — `home_surface_tapped` / `search_performed` / `learn_card_action` → play
6. **growth** — `install_attributed` + `deep_link_opened` + `share_content`
7. **library** — `library_destination_viewed`, `queue_modified`, downloads
8. **delight / feedback** — `feedback_submitted` (replaces `survey_nps_*`, `engagement_prompt_shown`, `promoter_review_handoff`); **update PostHog survey display conditions** to the new event name
9. **auto** (after PR9) — connect/browse + `client_surface=android_auto` listening

### PR7 cutover notes
- Stop querying legacy names for live dashboards; use `replaces_legacy` only for historical ranges.
- Person props: prefer `first_seen_at` / `first_play_at` (`$set_once`); `first_seen_date` is legacy.
- Raw text: AI turns use `user_input_text`; search uses `search_query` (never hashed).
- Phase C events (`adaptive_ranking_status`, Auto chapters/transcripts, `proxy_fallback_triggered`, late-night safeguard) are **not emitted** until PR9.

---

## Person properties sheet

| property | set when | replaces_legacy | notes |
|:--|:--|:--|:--|
| first_seen_at | first process | first_seen_date | $set_once |
| onboarding_status | pending/completed/skipped_deeplink | keep key if same | |
| onboarding_method | ai_chat/manual_genre/search/import/skip/deeplink_skip | extend | |
| install_channel | first open | (new) | |
| notifications_enabled | permission decide | keep | |
| initial_podcasts_subscribed | onboarding complete | keep | |
| first_play_at | first playback_started | (new) | $set_once |
| activated_at | onboarding complete + play ≤24h | (new) | $set_once |

