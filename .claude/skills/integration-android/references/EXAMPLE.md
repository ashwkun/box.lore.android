# PostHog Android Example Project

Repository: https://github.com/PostHog/context-mill
Path: basics/android

---

## README.md

# PostHog Android example

This is an Android example demonstrating PostHog integration with product analytics, session replay, and error tracking using Kotlin and Jetpack Compose.

This example uses the PostHog Android SDK (`posthog-android`) to provide automatic PostHog integration with built-in error tracking, session replay, and simplified configuration.

## Features

- **Product Analytics**: Track user events and behaviors
- **Session Replay**: Record and replay user sessions
- **Error Tracking**: Automatic error capture and crash reporting
- **User Authentication**: Demo login system with PostHog user identification
- **Event Tracking**: Examples of custom event tracking throughout the app

## Getting Started

### 1. Prerequisites

- Android Studio (latest stable version)
- Android SDK (API level 24 or higher)
- JDK 11 or higher
- Gradle 8.0 or higher
- A [PostHog account](https://app.posthog.com/signup)

### 2. Configure Environment Variables

The PostHog configuration is stored in `local.properties` (this file is gitignored):

```properties
# PostHog configuration
posthog.apiKey=your_posthog_project_token
posthog.host=https://us.i.posthog.com
```

Alternatively, you can configure PostHog in your `build.gradle` file:

```gradle
android {
    defaultConfig {
        buildConfigField "String", "POSTHOG_PROJECT_TOKEN", "\"your_posthog_project_token\""
        buildConfigField "String", "POSTHOG_HOST", "\"https://us.i.posthog.com\""
    }
}
```

Get your PostHog project token from your [PostHog project settings](https://app.posthog.com/project/settings).

### 3. Build and Run

1. Open the project in Android Studio
2. Sync Gradle files
3. Run the app on an emulator or physical device

## Project Structure

```
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/posthog/
│   │   │   │   ├── BurritoApplication.kt      # Application class with PostHog initialization
│   │   │   │   ├── MainActivity.kt           # Main activity
│   │   │   │   ├── ui/
│   │   │   │   │   ├── screens/
│   │   │   │   │   │   ├── LoginScreen.kt     # Login screen with user identification
│   │   │   │   │   │   ├── BurritoScreen.kt   # Demo feature screen with event tracking
│   │   │   │   │   │   └── ProfileScreen.kt   # User profile with error tracking demo
│   │   │   │   │   └── components/            # Reusable UI components
│   │   │   │   └── utils/
│   │   │   │       └── PostHogHelper.kt       # PostHog utility functions
│   │   │   ├── res/                           # Resources (layouts, strings, etc.)
│   │   │   └── AndroidManifest.xml            # App manifest
│   │   └── test/                              # Unit tests
│   └── build.gradle                           # App-level Gradle configuration
├── build.gradle                               # Project-level Gradle configuration
├── settings.gradle                            # Gradle settings
└── local.properties                           # Local configuration (gitignored)
```

## Key Integration Points

### Application Initialization (BurritoApplication.kt)

PostHog is initialized in the `Application` class to ensure it's available throughout the app lifecycle:

```kotlin
class BurritoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val posthogConfig = PostHogConfig(
            apiKey = BuildConfig.POSTHOG_PROJECT_TOKEN,
            host = BuildConfig.POSTHOG_HOST
        ).apply {
            // Enable session replay
            sessionReplay = true
            
            // Enable automatic exception capture
            captureApplicationLifecycleEvents = true
            captureDeepLinks = true
            captureScreenViews = true
        }
        
        PostHog.setup(this, posthogConfig)
    }
}
```

**Key Points:**
- PostHog is initialized in `onCreate()` to ensure it's initialized as early as possible
- Configuration is loaded from `BuildConfig` (set in `build.gradle`)
- Session replay, lifecycle events, and screen views are enabled
- The Application class must be registered in `AndroidManifest.xml`

### User Identification (LoginScreen.kt)

Users are identified when they log in:

```kotlin
val posthog = PostHog.getInstance()

fun handleLogin(username: String, password: String) {
    // Authenticate user
    val success = authenticateUser(username, password)
    
    if (success) {
        // Identify the user once on login/sign up
        posthog.identify(
            distinctId = username,
            properties = mapOf(
                "username" to username,
                "login_method" to "password"
            )
        )
        
        // Capture login event
        posthog.capture("user_logged_in", mapOf(
            "username" to username
        ))
    }
}
```

**Key Points:**
- `identify()` is called once when the user logs in or signs up
- User properties can be set during identification
- Events are captured using `capture()` with event names and properties
- The `distinctId` should be a unique identifier for the user

### Event Tracking (BurritoScreen.kt)

Custom events are tracked throughout the app:

```kotlin
val posthog = PostHog.getInstance()

fun handleBurritoConsideration() {
    // Track custom event
    posthog.capture("burrito_considered", mapOf(
        "total_considerations" to considerationCount,
        "username" to currentUser.username,
        "timestamp" to System.currentTimeMillis()
    ))
    
    // Update user properties
    posthog.setUserProperties(mapOf(
        "last_burrito_consideration" to System.currentTimeMillis(),
        "total_burrito_considerations" to considerationCount
    ))
}
```

**Key Points:**
- Events are captured with `capture()` method
- Event properties provide context about the event
- User properties can be updated with `setUserProperties()`
- Properties can be strings, numbers, booleans, or dates

### Error Tracking

Errors are captured automatically and can also be tracked manually:

**Automatic Error Capture:**
PostHog automatically captures uncaught exceptions when configured:

```kotlin
val posthogConfig = PostHogConfig(
    apiKey = BuildConfig.POSTHOG_PROJECT_TOKEN,
    host = BuildConfig.POSTHOG_HOST
).apply {
    // Automatic exception capture is enabled by default
    captureApplicationLifecycleEvents = true
}
```

**Manual Error Capture:**
```kotlin
val posthog = PostHog.getInstance()

try {
    // Risky operation
    performRiskyOperation()
} catch (e: Exception) {
    // Capture exception manually
    posthog.captureException(e, mapOf(
        "context" to "burrito_consideration",
        "user_id" to currentUser.id
    ))
}
```

### Screen View Tracking

Screen views are automatically tracked when `captureScreenViews` is enabled. You can also manually track screen views:

```kotlin
val posthog = PostHog.getInstance()

// Manual screen view tracking
posthog.screen("BurritoScreen", mapOf(
    "screen_category" to "features",
    "user_type" to "premium"
))
```

### Session Replay

Session replay is enabled in the PostHog configuration:

```kotlin
val posthogConfig = PostHogConfig(
    apiKey = BuildConfig.POSTHOG_PROJECT_TOKEN,
    host = BuildConfig.POSTHOG_HOST
).apply {
    sessionReplay = true
    sessionReplayConfig = SessionReplayConfig(
        maskAllInputs = false, // Set to true to mask all input fields
        maskAllText = false    // Set to true to mask all text
    )
}
```

### Accessing PostHog in Components

PostHog is accessed via the singleton instance:

```kotlin
val posthog = PostHog.getInstance()
posthog.capture("event_name", mapOf("property" to "value"))
```

The instance is available throughout your application after initialization.

## Gradle Configuration

### App-level build.gradle

```gradle
android {
    defaultConfig {
        // PostHog configuration
        buildConfigField "String", "POSTHOG_PROJECT_TOKEN", "\"${project.findProperty("posthog.apiKey") ?: ""}\""
        buildConfigField "String", "POSTHOG_HOST", "\"${project.findProperty("posthog.host") ?: "https://us.i.posthog.com"}\""
    }
}

dependencies {
    // PostHog Android SDK
    implementation 'com.posthog:posthog-android:3.+'
    
    // Other dependencies...
}
```

### Reading from local.properties

The `local.properties` file is automatically read by Gradle:

```gradle
def localProperties = new Properties()
localProperties.load(new FileInputStream(rootProject.file("local.properties")))

android {
    defaultConfig {
        buildConfigField "String", "POSTHOG_PROJECT_TOKEN", "\"${localProperties.getProperty("posthog.apiKey", "")}\""
        buildConfigField "String", "POSTHOG_HOST", "\"${localProperties.getProperty("posthog.host", "https://us.i.posthog.com")}\""
    }
}
```

## Best Practices

1. **Initialize Early**: Initialize PostHog in your `Application.onCreate()` method
2. **Identify Once**: Call `identify()` once when the user logs in or signs up
3. **Use Meaningful Event Names**: Use clear, descriptive event names (e.g., `user_logged_in` instead of `login`)
4. **Include Context**: Add relevant properties to events for better analysis
5. **Handle Errors Gracefully**: Don't let PostHog errors break your app
6. **Test in Development**: Use a separate PostHog project for development/testing
7. **Respect Privacy**: Be mindful of PII (Personally Identifiable Information) in events and properties

## Learn More

- [PostHog Documentation](https://posthog.com/docs)
- [Android Documentation](https://developer.android.com)
- [PostHog Android Integration Guide](https://posthog.com/docs/libraries/android)
- [PostHog Android SDK](https://github.com/PostHog/posthog-android)

---

## app/proguard-rules.pro

```pro
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
```

---

## app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".BurritoApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.PostHog">
        <!-- We have to export the MainActivity to be able to start it from the system launcher.
             Ignores added for semgrep. -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.PostHog"
            tools:ignore="ExportedActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

---

## app/src/main/java/com/example/posthog/BurritoApp.kt

```kt
package com.example.posthog

import android.app.Application
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

class BurritoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize PostHog early in Application lifecycle
        val config = PostHogAndroidConfig(
            apiKey = BuildConfig.POSTHOG_PROJECT_TOKEN,
            host = BuildConfig.POSTHOG_HOST,
        ).apply {
            debug = true
            errorTrackingConfig.autoCapture = true
        }
        
        PostHogAndroid.setup(this, config)
    }
}

```

---

## app/src/main/java/com/example/posthog/data/User.kt

```kt
package com.example.posthog.data

data class User(
    val username: String,
    val burritoConsiderations: Int = 0
)

```

---

## app/src/main/java/com/example/posthog/data/UserRepository.kt

```kt
package com.example.posthog.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

class UserRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "burrito_app_prefs"
        private const val KEY_CURRENT_USERNAME = "current_username"
        private const val KEY_USER_DATA_PREFIX = "user_data_"
    }

    fun getCurrentUsername(): String? {
        return prefs.getString(KEY_CURRENT_USERNAME, null)
    }

    fun getUser(username: String): User? {
        val json = prefs.getString("$KEY_USER_DATA_PREFIX$username", null) ?: return null
        return try {
            val obj = JSONObject(json)
            User(
                username = obj.getString("username"),
                burritoConsiderations = obj.getInt("burritoConsiderations")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveUser(user: User) {
        val json = JSONObject().apply {
            put("username", user.username)
            put("burritoConsiderations", user.burritoConsiderations)
        }.toString()

        prefs.edit()
            .putString("$KEY_USER_DATA_PREFIX${user.username}", json)
            .putString(KEY_CURRENT_USERNAME, user.username)
            .apply()
    }

    fun clearCurrentUser() {
        prefs.edit()
            .remove(KEY_CURRENT_USERNAME)
            .apply()
    }

    fun getCurrentUser(): User? {
        val username = getCurrentUsername() ?: return null
        return getUser(username)
    }
}

```

---

## app/src/main/java/com/example/posthog/MainActivity.kt

```kt
package com.example.posthog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.posthog.navigation.NavGraph
import com.example.posthog.navigation.Screen
import com.example.posthog.ui.components.AppHeader
import com.example.posthog.ui.components.BottomNavBar
import com.example.posthog.ui.theme.BackgroundGray
import com.example.posthog.ui.theme.PostHogTheme
import com.example.posthog.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PostHogTheme {
                BurritoApp()
            }
        }
    }
}

@Composable
fun BurritoApp() {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = viewModel()

    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppHeader(
                isAuthenticated = isAuthenticated,
                username = currentUser?.username,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.Home.route)
                        launchSingleTop = true
                    }
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                isAuthenticated = isAuthenticated,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.Home.route)
                        launchSingleTop = true
                    }
                }
            )
        },
        containerColor = BackgroundGray
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundGray)
                .padding(innerPadding)
        ) {
            NavGraph(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}

```

---

## app/src/main/java/com/example/posthog/navigation/NavGraph.kt

```kt
package com.example.posthog.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.posthog.ui.screens.BurritoScreen
import com.example.posthog.ui.screens.HomeScreen
import com.example.posthog.ui.screens.ProfileScreen
import com.example.posthog.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Burrito : Screen("burrito")
    object Profile : Screen("profile")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: AuthViewModel
) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                isAuthenticated = isAuthenticated,
                username = currentUser?.username,
                onLogin = { username -> viewModel.login(username) }
            )
        }

        composable(Screen.Burrito.route) {
            if (!isAuthenticated) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            } else {
                BurritoScreen(
                    burritoCount = currentUser?.burritoConsiderations ?: 0,
                    onConsiderBurrito = { viewModel.incrementBurritoCount() }
                )
            }
        }

        composable(Screen.Profile.route) {
            if (!isAuthenticated) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            } else {
                ProfileScreen(
                    username = currentUser?.username ?: "",
                    burritoCount = currentUser?.burritoConsiderations ?: 0
                )
            }
        }
    }
}

```

---

## app/src/main/java/com/example/posthog/ui/components/AppHeader.kt

```kt
package com.example.posthog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posthog.ui.theme.DarkHeader
import com.example.posthog.ui.theme.ErrorRed
import com.example.posthog.ui.theme.White

@Composable
fun AppHeader(
    isAuthenticated: Boolean,
    username: String?,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkHeader)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App title
            Text(
                text = "Burrito App",
                color = White,
                fontSize = 18.sp
            )

            // User section (right side)
            if (isAuthenticated && username != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = username,
                        color = White,
                        fontSize = 14.sp
                    )

                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Logout",
                            color = White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

```

---

## app/src/main/java/com/example/posthog/ui/components/BottomNavBar.kt

```kt
package com.example.posthog.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posthog.navigation.Screen
import com.example.posthog.ui.theme.PrimaryBlue
import com.example.posthog.ui.theme.TextGray
import com.example.posthog.ui.theme.White

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector?,
    val unselectedIcon: ImageVector?
) {
    object Home : BottomNavItem(
        route = Screen.Home.route,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object Burrito : BottomNavItem(
        route = Screen.Burrito.route,
        label = "Burrito",
        selectedIcon = null, // We'll use a custom icon or emoji
        unselectedIcon = null
    )

    object Profile : BottomNavItem(
        route = Screen.Profile.route,
        label = "Profile",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )
}

@Composable
fun BottomNavBar(
    isAuthenticated: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val items = if (isAuthenticated) {
        listOf(BottomNavItem.Home, BottomNavItem.Burrito, BottomNavItem.Profile)
    } else {
        listOf(BottomNavItem.Home)
    }

    NavigationBar(
        containerColor = White
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    if (item.selectedIcon != null && item.unselectedIcon != null) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        // For Burrito, use text emoji as icon
                        Text(
                            text = "🌯",
                            fontSize = 24.sp
                        )
                    }
                },
                label = {
                    Text(
                        text = item.label,
                        fontSize = 12.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryBlue,
                    selectedTextColor = PrimaryBlue,
                    unselectedIconColor = TextGray,
                    unselectedTextColor = TextGray,
                    indicatorColor = PrimaryBlue.copy(alpha = 0.1f)
                )
            )
        }
    }
}

```

---

## app/src/main/java/com/example/posthog/ui/components/StatsCard.kt

```kt
package com.example.posthog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posthog.ui.theme.LightGray
import com.example.posthog.ui.theme.TextDark
import com.example.posthog.ui.theme.TextGray

@Composable
fun StatsCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = LightGray,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = TextGray,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = TextDark,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

```

---

## app/src/main/java/com/example/posthog/ui/screens/BurritoScreen.kt

```kt
package com.example.posthog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posthog.ui.components.StatsCard
import com.example.posthog.ui.theme.BackgroundGray
import com.example.posthog.ui.theme.SuccessGreen
import com.example.posthog.ui.theme.TextDark
import com.example.posthog.ui.theme.TextGray
import com.example.posthog.ui.theme.White
import kotlinx.coroutines.delay

@Composable
fun BurritoScreen(
    burritoCount: Int,
    onConsiderBurrito: () -> Unit
) {
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(2000)
            showSuccess = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main content card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = TextDark.copy(alpha = 0.1f),
                        spotColor = TextDark.copy(alpha = 0.1f)
                    )
                    .background(
                        color = White,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Burrito consideration zone",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Take a moment to truly consider the burrito.",
                    fontSize = 16.sp,
                    color = TextGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        onConsiderBurrito()
                        showSuccess = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Consider the Burrito",
                        fontSize = 18.sp,
                        color = White
                    )
                }

                if (showSuccess) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "You have considered the burrito. Well done!",
                        color = SuccessGreen,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Consideration stats",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                StatsCard(
                    title = "Total Burrito Considerations",
                    value = burritoCount.toString()
                )
            }
        }
    }
}

```

---

## app/src/main/java/com/example/posthog/ui/screens/HomeScreen.kt

```kt
package com.example.posthog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posthog.ui.theme.BackgroundGray
import com.example.posthog.ui.theme.BorderGray
import com.example.posthog.ui.theme.PrimaryBlue
import com.example.posthog.ui.theme.TextDark
import com.example.posthog.ui.theme.TextGray
import com.example.posthog.ui.theme.White

@Composable
fun HomeScreen(
    isAuthenticated: Boolean,
    username: String?,
    onLogin: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = if (isAuthenticated) Alignment.TopCenter else Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isAuthenticated && username != null) {
                LoggedInContent(username = username)
            } else {
                LoginForm(onLogin = onLogin)
            }
        }
    }
}

@Composable
private fun LoggedInContent(username: String) {
    ContentCard {
        Text(
            text = "Welcome back, $username!",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ready to consider some burritos?",
            fontSize = 16.sp,
            color = TextGray,
            lineHeight = 26.sp
        )
    }
}

@Composable
private fun LoginForm(onLogin: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ContentCard {
        Text(
            text = "Welcome to Burrito Consideration App",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDark,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Username field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Username",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextDark,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderGray,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedContainerColor = White,
                    focusedContainerColor = White
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Password",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextDark,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderGray,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedContainerColor = White,
                    focusedContainerColor = White
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (username.isNotBlank()) {
                    onLogin(username)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryBlue
            ),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = "Sign In",
                fontSize = 16.sp,
                color = White
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Note: This is a demo app. Enter any username to sign in.",
            fontSize = 14.sp,
            color = TextGray,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
    }
}

@Composable
private fun ContentCard(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = TextDark.copy(alpha = 0.1f),
                spotColor = TextDark.copy(alpha = 0.1f)
            )
            .background(
                color = White,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        content()
    }
}

```

---

## app/src/main/java/com/example/posthog/ui/screens/ProfileScreen.kt

```kt
package com.example.posthog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.posthog.ui.components.StatsCard
import com.example.posthog.ui.theme.BackgroundGray
import com.example.posthog.ui.theme.TextDark
import com.example.posthog.ui.theme.TextGray
import com.example.posthog.ui.theme.White

@Composable
fun ProfileScreen(
    username: String,
    burritoCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main content card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = TextDark.copy(alpha = 0.1f),
                        spotColor = TextDark.copy(alpha = 0.1f)
                    )
                    .background(
                        color = White,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "User Profile",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Your Information section
                Text(
                    text = "Your Information",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Username display
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Username",
                        fontSize = 14.sp,
                        color = TextGray
                    )
                    Text(
                        text = username,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Stats card
                StatsCard(
                    title = "Total Burrito Considerations",
                    value = burritoCount.toString()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Your Burrito Journey section
                Text(
                    text = "Your Burrito Journey",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = getJourneyMessage(burritoCount),
                    fontSize = 16.sp,
                    color = TextGray,
                    textAlign = TextAlign.Start,
                    lineHeight = 26.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun getJourneyMessage(count: Int): String = when {
    count == 0 -> "You haven't considered any burritos yet. Start your journey!"
    count == 1 -> "You've considered the burrito potential once. The journey begins!"
    count in 2..4 -> "You're getting the hang of burrito consideration!"
    count in 5..9 -> "You're becoming a burrito consideration expert!"
    else -> "You are a true burrito consideration master!"
}

```

---

## app/src/main/java/com/example/posthog/ui/theme/Color.kt

```kt
package com.example.posthog.ui.theme

import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF0070F3)
val PrimaryBlueHover = Color(0xFF0051CC)
val SuccessGreen = Color(0xFF28A745)
val SuccessGreenHover = Color(0xFF218838)
val ErrorRed = Color(0xFFDC3545)
val ErrorRedHover = Color(0xFFC82333)
val DarkHeader = Color(0xFF333333)
val DarkHeaderHover = Color(0xFF555555)
val LightGray = Color(0xFFF8F9FA)
val BorderGray = Color(0xFFDDDDDD)
val TextGray = Color(0xFF666666)
val BackgroundGray = Color(0xFFF5F5F5)
val TextDark = Color(0xFF333333)
val White = Color(0xFFFFFFFF)

```

---

## app/src/main/java/com/example/posthog/ui/theme/Theme.kt

```kt
package com.example.posthog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = SuccessGreen,
    tertiary = DarkHeader,
    background = BackgroundGray,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = TextDark,
    onSurface = TextDark
)

@Composable
fun PostHogTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## app/src/main/java/com/example/posthog/ui/theme/Type.kt

```kt
package com.example.posthog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Typography based on design specification
// Uses system font stack (FontFamily.Default maps to Roboto on Android)
val Typography = Typography(
    // H1 - Page titles (32sp)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    // H2 - Section titles (24sp)
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // H3 - Subsection titles (20sp)
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // Body text (16sp with 1.6 line height = 25.6sp)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // Small/Note text (14sp)
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    // Labels (16sp, medium weight)
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // Button text - Burrito button (18sp)
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    // Button text - Primary/Logout (16sp/14sp)
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    )
)
```

---

## app/src/main/java/com/example/posthog/viewmodel/AuthViewModel.kt

```kt
package com.example.posthog.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.posthog.data.User
import com.example.posthog.data.UserRepository
import com.posthog.PostHog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserRepository(application)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            val user = repository.getCurrentUser()
            _currentUser.value = user
            _isAuthenticated.value = user != null
        }
    }

    fun login(username: String) {
        viewModelScope.launch {
            val existingUser = repository.getUser(username)
            val user = existingUser ?: User(username = username, burritoConsiderations = 0)
            repository.saveUser(user)
            _currentUser.value = user
            _isAuthenticated.value = true

            PostHog.identify(username)
            PostHog.capture(event = "user_logged_in")
        }
    }

    fun logout() {
        viewModelScope.launch {
            PostHog.capture("user_logged_out")
            PostHog.reset()
            repository.clearCurrentUser()
            _currentUser.value = null
            _isAuthenticated.value = false
        }
    }

    fun incrementBurritoCount() {
        viewModelScope.launch {
            val user = _currentUser.value ?: return@launch
            val updatedUser = user.copy(burritoConsiderations = user.burritoConsiderations + 1)
            repository.saveUser(updatedUser)
            _currentUser.value = updatedUser

            PostHog.capture(
                event = "burrito_considered",
                properties = mapOf(
                    "total_considerations" to updatedUser.burritoConsiderations,
                    "username" to updatedUser.username
                )
            )
        }
    }
}

```

---

## app/src/main/res/drawable/ic_launcher_background.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#3DDC84"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#00000000"
        android:pathData="M9,0L9,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,0L19,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M29,0L29,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M39,0L39,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M49,0L49,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M59,0L59,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M69,0L69,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M79,0L79,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M89,0L89,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M99,0L99,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,9L108,9"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,19L108,19"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,29L108,29"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,39L108,39"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,49L108,49"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,59L108,59"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,69L108,69"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,79L108,79"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,89L108,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,99L108,99"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,29L89,29"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,39L89,39"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,49L89,49"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,59L89,59"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,69L89,69"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,79L89,79"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M29,19L29,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M39,19L39,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M49,19L49,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M59,19L59,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M69,19L69,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M79,19L79,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
</vector>

```

---

## app/src/main/res/drawable/ic_launcher_foreground.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M31,63.928c0,0 6.4,-11 12.1,-13.1c7.2,-2.6 26,-1.4 26,-1.4l38.1,38.1L107,108.928l-32,-1L31,63.928z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="85.84757"
                android:endY="92.4963"
                android:startX="42.9492"
                android:startY="49.59793"
                android:type="linear">
                <item
                    android:color="#44000000"
                    android:offset="0.0" />
                <item
                    android:color="#00000000"
                    android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:fillColor="#FFFFFF"
        android:fillType="nonZero"
        android:pathData="M65.3,45.828l3.8,-6.6c0.2,-0.4 0.1,-0.9 -0.3,-1.1c-0.4,-0.2 -0.9,-0.1 -1.1,0.3l-3.9,6.7c-6.3,-2.8 -13.4,-2.8 -19.7,0l-3.9,-6.7c-0.2,-0.4 -0.7,-0.5 -1.1,-0.3C38.8,38.328 38.7,38.828 38.9,39.228l3.8,6.6C36.2,49.428 31.7,56.028 31,63.928h46C76.3,56.028 71.8,49.428 65.3,45.828zM43.4,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2c-0.3,-0.7 -0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C45.3,56.528 44.5,57.328 43.4,57.328L43.4,57.328zM64.6,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2s-0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C66.5,56.528 65.6,57.328 64.6,57.328L64.6,57.328z"
        android:strokeWidth="1"
        android:strokeColor="#00000000" />
</vector>
```

---

## app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---

## app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---

## app/src/main/res/values/colors.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
```

---

## app/src/main/res/values/strings.xml

```xml
<resources>
    <string name="app_name">PostHog</string>
</resources>
```

---

## app/src/main/res/values/themes.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="Theme.PostHog" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

---

## app/src/main/res/xml/backup_rules.xml

```xml
<?xml version="1.0" encoding="utf-8"?><!--
   Sample backup rules file; uncomment and customize as necessary.
   See https://developer.android.com/guide/topics/data/autobackup
   for details.
   Note: This file is ignored for devices older than API 31
   See https://developer.android.com/about/versions/12/backup-restore
-->
<full-backup-content>
    <!--
   <include domain="sharedpref" path="."/>
   <exclude domain="sharedpref" path="device.xml"/>
-->
</full-backup-content>
```

---

## app/src/main/res/xml/data_extraction_rules.xml

```xml
<?xml version="1.0" encoding="utf-8"?><!--
   Sample data extraction rules file; uncomment and customize as necessary.
   See https://developer.android.com/about/versions/12/backup-restore#xml-changes
   for details.
-->
<data-extraction-rules>
    <cloud-backup>
        <!-- TODO: Use <include> and <exclude> to control what is backed up.
        <include .../>
        <exclude .../>
        -->
    </cloud-backup>
    <!--
    <device-transfer>
        <include .../>
        <exclude .../>
    </device-transfer>
    -->
</data-extraction-rules>
```

---

## gradle.properties

```properties
# Project-wide Gradle settings.
# IDE (e.g. Android Studio) users:
# Gradle settings configured through the IDE *will override*
# any settings specified in this file.
# For more details on how to configure your build environment visit
# http://www.gradle.org/docs/current/userguide/build_environment.html
# Specifies the JVM arguments used for the daemon process.
# The setting is particularly useful for tweaking memory settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
# When configured, Gradle will run in incubating parallel mode.
# This option should only be used with decoupled projects. For more details, visit
# https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects
# org.gradle.parallel=true
# AndroidX package structure to make it clearer which packages are bundled with the
# Android operating system, and which are packaged with your app's APK
# https://developer.android.com/topic/libraries/support-library/androidx-rn
android.useAndroidX=true
# Kotlin code style for this project: "official" or "obsolete":
kotlin.code.style=official
# Enables namespacing of each library's R class so that its R class includes only the
# resources declared in the library itself and none from the library's dependencies,
# thereby reducing the size of the R class for that library
android.nonTransitiveRClass=true
```

---

## gradle/libs.versions.toml

```toml
[versions]
agp = "9.0.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.0.21"
composeBom = "2024.09.00"
navigationCompose = "2.7.5"
posthog = "3.31.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
posthog-android = { group = "com.posthog", name = "posthog-android", version.ref = "posthog" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }


```

---

## local.properties.example

```example
# Copy this file to local.properties and fill in your values
# local.properties is gitignored and will not be committed

sdk.dir=/path/to/your/android/sdk

# PostHog configuration
posthog.apiKey=your_posthog_project_token_here
posthog.host=https://us.i.posthog.com

```

---

