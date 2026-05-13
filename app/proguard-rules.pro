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
-keep interface cx.aswin.boxcast.** { *; }
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
-keep class cx.aswin.boxcast.core.model.** { *; }
-keep class cx.aswin.boxcast.core.network.** { *; }
-keep class cx.aswin.boxcast.core.network.model.** { *; }
-keep class cx.aswin.boxcast.core.data.** { *; }

# Also keep Kotlin Metadata to ensure reflection works (sometimes needed by Kotlinx Serialization/Gson adapters)
-keep class kotlin.Metadata { *; }

# Keep BuildConfig
-keep class cx.aswin.boxcast.BuildConfig { *; }
-keep class cx.aswin.boxcast.core.network.BuildConfig { *; }

# Fix R8 compilation errors due to missing Kotlin 2.0 classes in transitive dependencies
-dontwarn kotlin.uuid.**
-dontwarn kotlinx.serialization.builtins.BuiltinSerializersKt
