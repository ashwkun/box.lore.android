# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}


# Attributes - Critical for Retrofit/Serialization/Coroutines
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# Keep all interfaces (Retrofit APIs, Listeners)
-keep interface cx.aswin.boxlore.** { *; }
-keep interface retrofit2.** { *; }

# Retrofit - Force keep everything to prevent internal reflection failures
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson (Used for Streaming)
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**

# Coil (Image Loading)
-keep class coil.** { *; }

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Room (if used deeper down, though often handled by ksp/kapt rules now)
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# BoxCast Specific
# Keep all data models used in Network/DB
# Force keep all data models used in Network/DB
# CRITICAL: Gson needs full access to these, and R8 is too aggressive with renaming fields even with keepclassmembers
-keep class cx.aswin.boxlore.core.model.** { *; }
-keep class cx.aswin.boxlore.core.network.** { *; }
-keep class cx.aswin.boxlore.core.network.model.** { *; }
# Dual-keep: transitional core.data (stubs + playback/catalog/database until later PRs) + aligned packages
-keep class cx.aswin.boxlore.core.data.** { *; }
-keep class cx.aswin.boxlore.core.prefs.** { *; }
-keep class cx.aswin.boxlore.core.analytics.** { *; }
-keep class cx.aswin.boxlore.core.rss.** { *; }
-keep class cx.aswin.boxlore.core.ranking.** { *; }
-keep class cx.aswin.boxlore.core.downloads.** { *; }

# Also keep Kotlin Metadata to ensure reflection works (sometimes needed by Kotlinx Serialization/Gson adapters)
-keep class kotlin.Metadata { *; }

# Keep BuildConfig
-keep class cx.aswin.boxlore.BuildConfig { *; }
-keep class cx.aswin.boxlore.core.network.BuildConfig { *; }

# Fix R8 compilation errors due to missing Kotlin 2.0 classes in transitive dependencies
-dontwarn kotlin.uuid.**
-dontwarn kotlinx.serialization.builtins.BuiltinSerializersKt

# Fix NoClassDefFoundError on OEM ROMs that strip android.window.SplashScreenView
-dontwarn android.window.SplashScreenView
-keep class androidx.core.splashscreen.** { *; }

# Strip logging calls for production Release builds to prevent IPC/formatting overhead.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
