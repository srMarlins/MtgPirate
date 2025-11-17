import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose) // REQUIRED for Compose Multiplatform with Kotlin 2.x
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.detekt)
}

group = "org.srmarlins"
val appVersion = (project.findProperty("appVersion") as String?)
    ?: System.getenv("APP_VERSION")
    ?: "1.0.0"
val appVersionCore = appVersion.substringBefore('-').substringBefore('+')
version = appVersion

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
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
                implementation(libs.kotlinx.coroutines.core)
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
