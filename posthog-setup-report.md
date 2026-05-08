<wizard-report>
# PostHog post-wizard report

The wizard has completed a deep integration of PostHog analytics into BoxCast, a podcast app built with Android (Kotlin) and Jetpack Compose.

## Summary of changes

- **`BoxCastApplication.kt`** (new file): Application class that initializes PostHog via `PostHogAndroid.setup()` on app start, with `captureApplicationLifecycleEvents` and `captureScreenViews` enabled. Registered in `AndroidManifest.xml` via `android:name=".BoxCastApplication"`.
- **`app/build.gradle.kts`**: Added PostHog dependency (`com.posthog:posthog-android:3.+`) and two `buildConfigField` entries reading `posthog.apiKey` and `posthog.host` from `local.properties`.
- **`local.properties`**: PostHog API key and host stored here (gitignored).
- **`feature/onboarding/build.gradle.kts`**, **`feature/info/build.gradle.kts`**, **`feature/explore/build.gradle.kts`**: PostHog dependency added.
- **`OnboardingViewModel.kt`**: `onboarding completed` (with genre/podcast counts) and `onboarding skipped` events.
- **`EpisodeInfoViewModel.kt`**: `episode played`, `episode liked`, `episode downloaded`, `episode added to queue` events.
- **`PodcastInfoViewModel.kt`**: `podcast subscribed`, `podcast unsubscribed`, and `episode played` events (from podcast detail screen).
- **`ExploreViewModel.kt`**: `search performed` (with result count) and `vibe selected` events.

## Event tracking table

| Event | Description | File |
|-------|-------------|------|
| `onboarding completed` | User completed onboarding; includes genres_selected, podcasts_subscribed, selected_genres | `OnboardingViewModel.kt` |
| `onboarding skipped` | User skipped the onboarding flow | `OnboardingViewModel.kt` |
| `podcast subscribed` | User subscribed to a podcast; includes podcast_id, podcast_title, podcast_genre | `PodcastInfoViewModel.kt` |
| `podcast unsubscribed` | User unsubscribed from a podcast | `PodcastInfoViewModel.kt` |
| `episode played` | User started playing an episode; includes episode_id, podcast_id, genre, source | `EpisodeInfoViewModel.kt`, `PodcastInfoViewModel.kt` |
| `episode liked` | User liked/unliked an episode; includes liked (bool) | `EpisodeInfoViewModel.kt` |
| `episode downloaded` | User downloaded an episode for offline listening | `EpisodeInfoViewModel.kt` |
| `episode added to queue` | User added an episode to the playback queue | `EpisodeInfoViewModel.kt` |
| `search performed` | User performed a podcast search; includes result_count, category | `ExploreViewModel.kt` |
| `vibe selected` | User selected a curated vibe/mood in Explore | `ExploreViewModel.kt` |

Automatic events captured by the SDK (no code required):
- `Application Installed`, `Application Updated`, `Application Opened`, `Application Backgrounded`, `$screen` (screen views)

## Next steps

We've built some insights and a dashboard for you to keep an eye on user behavior, based on the events we just instrumented:

- **Dashboard — Analytics basics**: https://us.posthog.com/project/411767/dashboard/1561076
- **Onboarding Funnel** (conversion App Opened → Onboarding Completed): https://us.posthog.com/project/411767/insights/UE0CQwbI
- **Episode Plays Over Time** (daily trend): https://us.posthog.com/project/411767/insights/AS1NTi2B
- **Podcast Subscriptions vs Unsubscriptions** (weekly): https://us.posthog.com/project/411767/insights/FbzsulUy
- **Discovery Engagement** (Search & Vibe Selections): https://us.posthog.com/project/411767/insights/mLul7rZK
- **Episode Engagement Actions** (likes, downloads, queue adds): https://us.posthog.com/project/411767/insights/Q5WEY31A

### Agent skill

We've left an agent skill folder in your project. You can use this context for further agent development when using Claude Code. This will help ensure the model provides the most up-to-date approaches for integrating PostHog.

</wizard-report>
