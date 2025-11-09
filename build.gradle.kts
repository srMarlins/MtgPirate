plugins {
    kotlin("multiplatform") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.compose") version "1.9.3"
    kotlin("plugin.serialization") version "2.2.21"
}

// Centralized version declarations (adjust here when updating)
@Suppress("MemberVisibilityCanBePrivate")
object Versions {
    const val serialization = "1.9.0"
    const val datetime = "0.7.1"
    const val ksoup = "0.2.5"
    const val coroutines = "1.9.0"
}

group = "org.srmarlins"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm("desktop") {
    }
    // Configure toolchain and compiler options for all JVM targets (Kotlin 2.x DSL)
    jvmToolchain(17)
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.datetime}")
                implementation("com.fleeksoft.ksoup:ksoup:${Versions.ksoup}")
                implementation(compose.runtime)
                implementation(compose.foundation)
                // Navigation 3 (AndroidX Navigation Compose repackaged for Multiplatform)
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material)
                implementation(compose.foundation)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${Versions.coroutines}")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "USEACardScraper"
            packageVersion = "1.0.0"
            // Optional metadata
            description = "USEA Card Scraper desktop application"
        }
    }
}
