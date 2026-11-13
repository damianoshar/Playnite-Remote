# R8 / ProGuard rules for Playnite Remote.

# --- Gson (DTOs use @SerializedName, but the classes themselves are
#     reflected over by name) -----------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Model classes are serialized to/from the offline cache by field name.
-keep class com.gamevault.unified.data.model.** { *; }
-keep class com.gamevault.unified.data.local.CachedLibrary { *; }
-keep class com.gamevault.unified.data.remote.** { *; }

# --- Retrofit / OkHttp -----------------------------------------------------
-keepattributes Exceptions
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepclasseswithmembers,includedescriptorclasses class * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# --- Kotlin coroutines -----------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembers class kotlin.coroutines.SafeContinuation { <fields>; }

# --- App widget (RemoteViews inflation) ------------------------------------
-keep class com.gamevault.unified.GameLibraryWidget { *; }

# --- Keep line numbers for readable crash traces ----------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
