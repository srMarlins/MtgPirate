# ProGuard/R8 rules for DeckLoot Android release

# Ktor OkHttp engine
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.deckloot.**$$serializer { *; }
-keepclassmembers class com.deckloot.** {
    *** Companion;
}
-keepclasseswithmembers class com.deckloot.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLDelight Android driver
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# Coil image loading
-keep class coil3.** { *; }
-dontwarn coil3.**

# Keep Compose runtime
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# KSoup HTML parsing
-keep class com.fleeksoft.ksoup.** { *; }
-dontwarn com.fleeksoft.ksoup.**
