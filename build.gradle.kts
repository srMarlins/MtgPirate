import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose) // REQUIRED for Compose Multiplatform with Kotlin 2.x
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

group = "org.srmarlins"

// Retrieve the app version from:
// 1. Gradle property 'appVersion' (passed via -PappVersion=...)
// 2. Environment variable 'APP_VERSION'
// 3. Default fallback "1.0.0"
val appVersion = providers.gradleProperty("appVersion")
    .orElse(providers.environmentVariable("APP_VERSION"))
    .getOrElse("1.0.0")

// Core version for native distributions (strips SemVer suffixes like -rc.1)
// Windows/macOS installers often require strict X.Y.Z format
val appVersionCore = appVersion.substringBefore('-').substringBefore('+')

version = appVersion

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
    jvmToolchain(17)
    jvm("desktop")
    iosX64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }
    iosArm64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "shared"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }
    
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ksoup)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.navigation.compose)
                implementation(libs.sqldelight.coroutines.extensions)
                implementation(libs.sqldelight.runtime)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqldelight.driver.sqlite)
                implementation(libs.ktor.client.cio)
            }
        }

        // Use default Kotlin hierarchy template for intermediate source sets
        // Default hierarchy automatically creates appleMain for iOS + macOS targets
        // See: https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
        val appleMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
                implementation(libs.sqldelight.driver.native)
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.MainKt"
        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Exe,
                TargetFormat.Deb
            )
            packageName = "MtgPirate"
            packageVersion = appVersionCore
            
            modules("java.sql")
            
            // Configure app icons for all platforms
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icons/icon_512.png"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/icon_256.png"))
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icons/icon_512.png"))
            }
        }
    }
}

sqldelight {
    databases {
        create("MtgPirateDatabase") {
            packageName.set("org.srmarlins.mtgpirate.db")
        }
    }
}
