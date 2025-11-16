import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
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

kotlin {
    jvm("desktop") {}
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    // Apply default hierarchy template explicitly
    applyDefaultHierarchyTemplate()
    
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
                implementation(libs.sqldelight.coroutines.extensions)
                implementation(libs.sqldelight.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.material)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqldelight.driver.sqlite)
                implementation(compose.desktop.currentOs)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        
        // Use default Kotlin hierarchy template for intermediate source sets
        // Default hierarchy automatically creates appleMain for iOS + macOS targets
        // See: https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
        val appleMain by getting {
            dependencies {
                implementation(libs.sqldelight.driver.native)
            }
        }
        val iosMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
            }
        }
        val macosMain by getting {
            dependencies {
                implementation(compose.desktop.macos_arm64)
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

sqldelight {
    databases {
        create("MtgPirateDatabase") {
            packageName.set("com.srmarlins.mtgpirate")
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/detekt.yml")
    baseline = file("$projectDir/detekt-baseline.xml")
    source.setFrom(
        "src/commonMain/kotlin",
        "src/desktopMain/kotlin",
        "src/jvmMain/kotlin",
        "src/iosMain/kotlin",
        "src/macosMain/kotlin",
        "src/appleMain/kotlin"
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}
