# ProGuard rules for Compose Desktop
# Keep SQLite JDBC driver - it uses reflection to load the driver
-keep class org.sqlite.** { *; }
-keep class org.sqlite.JDBC { *; }
-keep class org.sqlite.core.** { *; }
-keep class org.sqlite.jdbc3.** { *; }
-keep class org.sqlite.jdbc4.** { *; }

# Keep all JDBC drivers (they use Class.forName)
-keep class * implements java.sql.Driver { *; }

# Keep database-related classes that might use reflection
-keepclassmembers class * {
    @androidx.room.** *;
}

# Prevent obfuscation of SQL-related classes
-keep class java.sql.** { *; }

# Keep Kotlin coroutines for database operations
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

# Keep Compose runtime
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }

# Don't warn about missing classes
-dontwarn org.slf4j.**
-dontwarn org.apache.log4j.**
