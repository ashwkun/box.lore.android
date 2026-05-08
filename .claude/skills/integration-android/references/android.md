# Android - Docs

It uses an internal queue to make calls fast and non-blocking. It also batches requests and flushes asynchronously, making it perfect to use in any part of your mobile app.

## Installation

The best way to install the PostHog Android library is with a build system like [Gradle](https://gradle.org/). This ensures you can easily upgrade to the latest versions.

All you need to do is add the `posthog-android` module to your App's `build.gradle` or `build.gradle.kts`:

PostHog AI

### app/build.gradle

```gradle
dependencies {
  implementation 'com.posthog:posthog-android:3.+'
}
```

### app/build.gradle.kts

```kotlin
dependencies {
  implementation("com.posthog:posthog-android:3.+")
}
```

### Configuration

The best place to initialize the client is in your `Application` subclass.

Kotlin

PostHog AI

```kotlin
import android.app.Application
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
class SampleApp : Application() {
    companion object {
        const val POSTHOG_API_KEY = "<ph_project_token>"
        // usually 'https://us.i.posthog.com' or 'https://eu.i.posthog.com'
        const val POSTHOG_HOST = "https://us.i.posthog.com"
    }
    override fun onCreate() {
        super.onCreate()
        val config = PostHogAndroidConfig(
            apiKey = POSTHOG_API_KEY,
            host = POSTHOG_HOST
        )
        PostHogAndroid.setup(this, config)
    }
}
```

## Capturing events

You can send custom events using `capture`:

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.capture(event = "user_signed_up")
```

> **Tip:** We recommend using a `[object] [verb]` format for your event names, where `[object]` is the entity that the behavior relates to, and `[verb]` is the behavior itself. For example, `project created`, `user signed up`, or `invite sent`.

### Setting event properties

Optionally, you can include additional information with the event by including a [properties](/docs/data/events.md#event-properties) object:

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.capture(
    event = "user_signed_up",
    properties = mapOf(
        "login_type" to "email",
        "is_free_trial" to true
    )
)
```

### Autocapture

PostHog autocapture automatically tracks the following events for you:

-   **Application Opened** - when the app is opened from a closed state or when the app comes to the foreground. (e.g. from the app switcher)
-   **Deep Link Opened** - when the app is opened from a deep link.
-   **Application Backgrounded** - when the app is sent to the background by the user.
-   **Application Installed** - when the app is installed.
-   **Application Updated** - when the app is updated.
-   **$screen** - when the user navigates. (if using `android.app.Activity`)
-   **$exception** - when the app throws exceptions.

### Capturing screen views

With [`captureScreenViews = true`](/docs/libraries/android.md#all-configuration-options), PostHog will try to record all screen changes automatically.

The `screenTitle` will be the [`<activity>`](https://developer.android.com/guide/topics/manifest/activity-element)'s `android:label`, if not set it'll fallback to the [`<application>`](https://developer.android.com/guide/topics/manifest/application-element)'s `android:label` or the [`<activity>`](https://developer.android.com/guide/topics/manifest/activity-element)'s `android:name`.

XML

PostHog AI

```xml
<activity
    android:name="com.example.app.ChildActivity"
    android:label="@string/title_child_activity"
    ...
</activity>
```

If you want to manually send a new screen capture event, use the `screen` function.

This function requires a `screenTitle`. You may also pass in an optional `properties` object.

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.screen(
    screenTitle = "Dashboard",
    properties = mapOf(
        "background" to "blue",
        "hero" to "superhog"
    )
)
```

## Identifying users

> We highly recommend reading our section on [Identifying users](/docs/integrate/identifying-users.md) to better understand how to correctly use this method.

Using `identify`, you can associate events with specific users. This enables you to gain full insights as to how they're using your product across different sessions, devices, and platforms.

An `identify` call has the following arguments:

-   **distinctId:** Required. A unique identifier for your user. Typically either their email or database ID.
-   **userProperties:** Optional. A dictionary with key:value pairs to set the [person properties](/docs/product-analytics/person-properties.md)
-   **userPropertiesSetOnce:** Optional. Similar to `userProperties`. [See the difference between `userProperties` and `userPropertiesSetOnce`](/docs/product-analytics/person-properties.md#what-is-the-difference-between-set-and-set_once)

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.identify(
    distinctId = distinctID,
    userProperties = mapOf(
        "name" to "Max Hedgehog",
        "email" to "max@hedgehogmail.com"
    ),
    userPropertiesSetOnce = mapOf(
        "date_of_first_log_in" to "2024-03-01"
    ),
)
```

You should call `identify` as soon as you're able to. Typically, this is after your user logs in. This ensures that events sent during your user's sessions are correctly associated with them.

When you call `identify`, all previously tracked anonymous events will be linked to the user.

## Get the current user's distinct ID

You may find it helpful to get the current user's distinct ID. For example, to check whether you've already called `identify` for a user or not.

To do this, call `distinctId()`. This returns either the ID automatically generated by PostHog or the ID that has been passed by a call to `identify()`.

## Alias

Sometimes, you want to assign multiple distinct IDs to a single user. This is helpful when your primary distinct ID is inaccessible. For example, if a distinct ID used on the frontend is not available in your backend.

In this case, you can use `alias` to assign another distinct ID to the same user.

Kotlin

PostHog AI

```kotlin
/**
 * Create an alias for the current user.
 */
PostHog.alias("distinct_id")
```

We strongly recommend reading our docs on [alias](/docs/data/identify.md#alias-assigning-multiple-distinct-ids-to-the-same-user) to best understand how to correctly use this method.

## Anonymous and identified events

PostHog captures two types of events: [**anonymous** and **identified**](/docs/data/anonymous-vs-identified-events.md)

**Identified events** enable you to attribute events to specific users, and attach [person properties](/docs/product-analytics/person-properties.md). They're best suited for logged-in users.

Scenarios where you want to capture identified events are:

-   Tracking logged-in users in B2B and B2C SaaS apps
-   Doing user segmented product analysis
-   Growth and marketing teams wanting to analyze the *complete* conversion lifecycle

**Anonymous events** are events without individually identifiable data. They're best suited for [web analytics](/docs/web-analytics.md) or apps where users aren't logged in.

Scenarios where you want to capture anonymous events are:

-   Tracking a marketing website
-   Content-focused sites
-   B2C apps where users don't sign up or log in

Under the hood, the key difference between identified and anonymous events is that for identified events we create a [person profile](/docs/data/persons.md) for the user, whereas for anonymous events we do not.

> **Important:** Due to the reduced cost of processing them, anonymous events can be up to 4x cheaper than identified ones, so we recommended you only capture identified events when needed.

### How to capture anonymous events

The Android SDK captures anonymous events by default. However, this may change depending on your `personProfiles` [config](/docs/libraries/android.md#all-configuration-options) when initializing PostHog:

1.  `personProfiles = PersonProfiles.IDENTIFIED_ONLY` *(recommended)* *(default)* - Anonymous events are captured by default. PostHog only captures identified events for users where [person profiles](/docs/data/persons.md) have already been created.

2.  `personProfiles = PersonProfiles.ALWAYS` - Capture identified events for all events.

3.  `personProfiles = PersonProfiles.NEVER` - Capture anonymous events for all events.

For example:

Kotlin

PostHog AI

```kotlin
val config = PostHogAndroidConfig(
   apiKey = POSTHOG_API_KEY,
   host = POSTHOG_HOST,
).apply {
   personProfiles = PersonProfiles.IDENTIFIED_ONLY
}
```

### How to capture identified events

If you've set the [`personProfiles` config](/docs/libraries/android.md#all-configuration-options) to `IDENTIFIED_ONLY` (the default option), anonymous events are captured by default. Then, to capture identified events, call any of the following functions:

-   [`identify()`](/docs/product-analytics/identify.md)
-   [`alias()`](/docs/product-analytics/identify.md#alias-assigning-multiple-distinct-ids-to-the-same-user)
-   [`group()`](/docs/product-analytics/group-analytics.md)

When you call any of these functions, it creates a [person profile](/docs/data/persons.md) for the user. Once this profile is created, all subsequent events for this user will be captured as identified events.

Alternatively, you can set `personProfiles` to `ALWAYS` to capture identified events by default.

## Setting person properties

To set [properties](/docs/data/user-properties.md) on your users via an event, you can leverage the event properties `userProperties` and `userPropertiesSetOnce`.

When capturing an event, you can pass a property called `userProperties` as an event property, and specify its value to be an object with properties to be set on the user that will be associated with the user who triggered the event.

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.capture(
    event = "button_b_clicked",
    properties = mapOf("color" to "blue"),
    userProperties = mapOf(
        "string" to "value1",
        "integer" to 2
    )
)
```

`userPropertiesSetOnce` works just like `userProperties`, except that it will **only set the property if the user doesn't already have that property set**.

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.capture(
    event = "button_b_clicked",
    properties = mapOf("color" to "blue"),
    userPropertiesSetOnce = mapOf(
        "string" to "value1",
        "integer" to 2
    )
)
```

## Super Properties

Super Properties are properties associated with events that are set once and then sent with every `capture` call, be it a `$screen`, or anything else.

They are set using `PostHog.register`, which takes a key and value, and they persist across sessions.

For example, take a look at the following call:

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.register("team_id", 22)
```

The call above ensures that every event sent by the user will include `"team_id": 22`. This way, if you filtered events by property using `team_id = 22`, it would display all events captured on that user after the `PostHog.register` call, since they all include the specified Super Property.

However, please note that this does not store properties against the User, only against their events. To store properties against the User object, you should use `PostHog.identify`. More information on this can be found on the [Sending User Information section](#sending-user-information).

### Removing stored Super Properties

Super Properties are persisted across sessions so you have to explicitly remove them if they are no longer relevant. In order to stop sending a Super Property with events, you can use `PostHog.unregister`, like so:

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.unregister("team_id")
```

This will remove the Super Property and subsequent events will not include it.

If you are doing this as part of a user logging out you can instead simply use `PostHog.reset` which takes care of clearing all stored Super Properties and more.

## Opt out of data capture

You can completely opt-out users from data capture. To do this, there are two options:

1.  Opt users out by default by setting `optOut` to `true` in your PostHog config:

Kotlin

PostHog AI

```kotlin
val config = PostHogAndroidConfig(
    apiKey = <ph_project_token>,
    host = https://us.i.posthog.com
)
config.optOut = true
PostHogAndroid.setup(this, config)
```

2.  Opt users out on a per-person basis by calling `optOut()`:

Kotlin

PostHog AI

```kotlin
PostHog.optOut()
```

Similarly, you can opt users in:

Kotlin

PostHog AI

```kotlin
PostHog.optIn()
```

To check if a user is opted out:

Kotlin

PostHog AI

```kotlin
PostHog.isOptOut()
```

## Flush

You can set the number of events in the configuration that should queue before flushing. Setting this to `1` will send events immediately and will use more battery. The default value for this is `20`.

You can also configure the flush interval. By default we flush all events after `30` seconds, no matter how many events have been gathered.

Kotlin

PostHog AI

```kotlin
import com.posthog.android.PostHogAndroidConfig
val config = PostHogAndroidConfig(apiKey = POSTHOG_API_KEY, host = POSTHOG_HOST).apply {
    flushAt = 20
    flushIntervalSeconds = 30
}
```

You can also manually flush the queue:

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.flush()
```

## Reset after logout

To reset the user's ID and anonymous ID, call `reset`. Usually you would do this right after the user logs out.

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.reset()
```

## Feature Flags

PostHog's [feature flags](/docs/feature-flags.md) enable you to safely deploy and roll back new features as well as target specific users and groups with them.

### Boolean feature flags

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
if (PostHog.isFeatureEnabled("flag-key")) {
    // Do something differently for this user
    // Optional: fetch the payload
    val matchedFlagPayload = PostHog.getFeatureFlagPayload("flag-key")
}
```

### Multivariate feature flags

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
if (PostHog.getFeatureFlag("flag-key") == "variant-key") { // replace 'variant-key' with the key of your variant
    // Do something differently for this user
    // Optional: fetch the payload
    val matchedFlagPayload = PostHog.getFeatureFlagPayload("flag-key")
}
```

### Ensuring flags are loaded before usage

Every time a user opens the app, we send a request in the background to fetch the feature flags that apply to that user. We store those flags in the storage.

This means that for most screens, the feature flags are available immediately – **except for the first time a user visits**.

To handle this, you can use the `onFeatureFlags` callback to wait for the feature flag request to finish:

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
import com.posthog.android.PostHogAndroidConfig
import com.posthog.PostHogOnFeatureFlags
// During SDK initialization
val config = PostHogAndroidConfig(apiKey = "<ph_project_token>").apply {
    onFeatureFlags = PostHogOnFeatureFlags {
        if (PostHog.isFeatureEnabled("flag-key")) {
            // do something
        }
    }
}
// And/Or manually the SDK is initialized
PostHog.reloadFeatureFlags {
    if (PostHog.isFeatureEnabled("flag-key")) {
        // do something
    }
}
```

### Reloading feature flags

Feature flag values are cached. If something has changed with your user and you'd like to refetch their flag values, call:

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.reloadFeatureFlags()
```

## Experiments (A/B tests)

Since [experiments](/docs/experiments/start-here.md) use feature flags, the code for running an experiment is very similar to the feature flags code:

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
if (PostHog.getFeatureFlag("experiment-feature-flag-key") == "variant-name") {
    // do something
}
```

It's also possible to [run experiments without using feature flags](/docs/experiments/running-experiments-without-feature-flags.md).

## Group analytics

Group analytics allows you to associate the events for that person's session with a group (e.g. teams, organizations, etc.). Read the [Group Analytics](/docs/user-guides/group-analytics.md) guide for more information.

> **Note:** This is a paid feature and is not available on the open-source or free cloud plan. Learn more on the [pricing page](/pricing.md).

-   Associate the events for this session with a group

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
// organization is the group type, company_id_in_your_db is the group ID
PostHog.group(
    type = "company",
    key = "company_id_in_your_db"
)
```

-   Associate the events for this session with a group AND update the properties of that group

Kotlin

PostHog AI

```kotlin
import com.posthog.PostHog
PostHog.group(
    type = "company",
    key = "company_id_in_your_db",
    groupProperties = mapOf("name" to "Awesome Inc.")
)
```

The `name` is a special property which is used in the PostHog UI for the name of the group. If you don't specify a `name` property, the group ID will be used instead.

## Error tracking

To set up error tracking in your project, follow the [Android installation guide](/docs/error-tracking/installation/android.md).

## Session replay

To set up [session replay](/docs/session-replay/mobile.md) in your project, all you need to do is install the Android SDK, enable "Record user sessions" in [your project settings](https://us.posthog.com/settings/project-replay) and enable the `sessionReplay` option.

## Offline behavior

The PostHog Android SDK will continue to capture events when the device is offline. The events are stored in a queue in the device's file storage and are flushed when the device is online.

-   The queue has a maximum size defined by `maxQueueSize` in the configuration.
-   When the queue is full, the oldest event is deleted first.
-   The queue is flushed when the app is restarted and the device is online.
-   When you call [`flush()`](#flush) while the device is offline, it aborts early and the events are not flushed.

## Debug mode

If you're not seeing the expected events being captured, the feature flags being evaluated, or the surveys being shown, you can enable debug mode to see what's happening.

You can enable debug mode by setting the `debug` option to `true` in the `PostHogAndroidConfig` object. This will enable verbose logs about the inner workings of the SDK.

Kotlin

PostHog AI

```kotlin
val config = PostHogAndroidConfig(apiKey = POSTHOG_API_KEY, host = POSTHOG_HOST).apply {
    debug = true
    // ... other config options
}
```

## All configuration options

When creating the PostHog client, there are many options you can set:

Kotlin

PostHog AI

```kotlin
val config = PostHogAndroidConfig(apiKey = POSTHOG_API_KEY, host = POSTHOG_HOST).apply {
    // Capture certain application events automatically. (on/true by default)
    captureApplicationLifecycleEvents = true
    // Capture screen views automatically. (on/true by default)
    captureScreenViews = true // (on/true by default)
    // Capture deep links as part of the screen call. (on/true by default)
    captureDeepLinks = true
    // Maximum number of events to keep in queue before flushing (20 by default)
    flushAt = 20
    // Number of maximum events in memory and disk, when the maximum is exceed, the oldest event is deleted and the new one takes place. (1000 by default)
    maxQueueSize = 1000
    // Number of maximum events in a batch call. (50 by default)
    maxBatchSize = 50
    // Maximum delay before flushing the queue (30 seconds)
    flushIntervalSeconds = 30
    // Logs the SDK messages into Logcat. (off/false by default)
    debug = false
    // Prevents capturing any data if enabled. (off/false by default)
    optOut = false
    // Send a '$feature_flag_called' event when a feature flag is used automatically. (on/true by default)
    sendFeatureFlagEvent = true
    // Preload feature flags automatically. (on/true by default)
    preloadFeatureFlags = true
    // Evaluation context tags that constrain which feature flags are evaluated. (not set by default)
    // When set, only flags with matching evaluation context tags (or no evaluation context tags) will be returned.
    // Available in version 3.25.0+. The legacy parameter `evaluationEnvironments` (version 3.24.0+) is also supported.
    evaluationContexts = listOf("production", "android", "mobile")
    // Callback that is called when feature flags are loaded (not set by default)
    onFeatureFlags = { ... }
    // Callback that allows to sanitize the event properties (not set by default)
    propertiesSanitizer = { properties -> ... }
    // Hook for encrypt and decrypt events
    // Devices are sandbox already
    // Defaults to no encryption
    encryption = object : PostHogEncryption { ... }
    // Hook that allows for modification of the default mechanism for
    // generating anonymous id (which as of now is just random UUID v7)
    getAnonymousId = { ... }
    // Determines the behavior for processing user profiles.
    // Defaults to PersonProfiles.IDENTIFIED_ONLY
    personProfiles = PersonProfiles.IDENTIFIED_ONLY
    // Enable Recording of Session Replay. (off/false by default)
    sessionReplay = false
    // Session Replay configuration
    // https://posthog.com/docs/session-replay/installation for more details
    sessionReplayConfig = PostHogSessionReplayConfig(...)
    // Whether the SDK should reuse the anonymous Id between user changes.
    // When enabled, a single Id will be used for all anonymous users on this device (off/false by default)
    reuseAnonymousId = false
    // Error tracking configuration
    errorTrackingConfig = PostHogErrorTrackingConfig(...)
}
```

## FAQ

## Do I need to declare permissions in the AndroidManifest.xml?

We don't declare nor use any 'Service', so no permissions are needed.

### Community questions

Ask a question

### Was this page useful?

HelpfulCould be better