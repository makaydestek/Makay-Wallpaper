# Makay Wallpaper - R8 (boyut + sideload kararliligi)

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Gson / TypeToken (R8)
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.makay.wallpaper.domain.model.** { *; }
-keep class com.makay.wallpaper.data.** { *; }

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class com.makay.wallpaper.worker.** { *; }

# Manifest bilesenleri
-keep class com.makay.wallpaper.MakayWallpaperApplication { *; }
-keep class com.makay.wallpaper.MainActivity { *; }
-keep class com.makay.wallpaper.widget.** { *; }
-keep class com.makay.wallpaper.receiver.** { *; }
-keep class com.makay.wallpaper.service.** { *; }

# Tink / security-crypto
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn androidx.security.crypto.**
-keep class androidx.security.crypto.** { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
