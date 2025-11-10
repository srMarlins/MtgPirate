import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
}

group = "org.srmarlins"
val appVersion = (project.findProperty("appVersion") as String?)
    ?: System.getenv("APP_VERSION")
    ?: "1.0.0"
// Use numeric core (x.y.z) for native packageVersion to satisfy platform requirements
val appVersionCore = appVersion.substringBefore('-').substringBefore('+')
version = appVersion

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

configurations.all {
    resolutionStrategy {
        // Force consistent versions for common dependencies
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        
        // Cache dynamic versions for 24 hours
        cacheDynamicVersionsFor(24, "hours")
        cacheChangingModulesFor(0, "seconds")
    }
}

kotlin {
    jvm("desktop") {
    }
    // Configure toolchain and compiler options for all JVM targets (Kotlin 2.x DSL)
    jvmToolchain(17)
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ksoup)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(libs.navigation.compose)
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
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
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
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Dmg
            )
            packageName = "MtgPirate"
            packageVersion = appVersionCore
            // Optional metadata
            description = "MtgPirate - MTG Deck Import and Price Calculator"

            macOS {
                // App bundle identifier for macOS packaging
                bundleID = "org.srmarlins.MtgPirate"

                // Configure signing and notarization via environment variables set in CI
                // Signing is enabled only when SIGN_MAC == "true"
                val enableSign = System.getenv("SIGN_MAC")?.toBoolean() == true
                signing {
                    sign.set(enableSign)
                    identity.set(System.getenv("MACOS_SIGNING_IDENTITY") ?: "")
                }
                notarization {
                    // Notarization runs only if credentials are present
                    appleID.set(System.getenv("MACOS_NOTARIZATION_APPLE_ID") ?: "")
                    password.set(System.getenv("MACOS_NOTARIZATION_PASSWORD") ?: "")
                }
            }
        }
    }
}

// Detekt static analysis configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt.yml")
    baseline = file("$projectDir/config/detekt-baseline.xml")
    
    source.setFrom(
        "src/commonMain/kotlin",
        "src/desktopMain/kotlin"
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}
