# ProGuard rules for Compose Desktop
# Keep SQLite JDBC driver - it uses reflection to load the driver
-keep class org.sqlite.** { *; }
-keep class org.sqlite.JDBC { *; }
-keep class org.sqlite.core.** { *; }
-keep class org.sqlite.jdbc3.** { *; }
-keep class org.sqlite.jdbc4.** { *; }

# Keep all JDBC drivers (they use Class.forName)
-keep class * implements java.sql.Driver { *; }

# Prevent obfuscation of SQL-related classes
-keep class java.sql.** { *; }

# Keep Kotlin coroutines for database and Ktor operations
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep serialization classes
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    *;
}
-keep,includedescriptorclasses class org.srmarlins.**$$serializer { *; }
-keepclassmembers class org.srmarlins.** {
    *** Companion;
}
-keepclasseswithmembers class org.srmarlins.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor CIO engine uses reflection
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**

# SLF4J service provider interface
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# Don't warn about missing optional logging backends
-dontwarn org.apache.log4j.**
