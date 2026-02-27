import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose) // REQUIRED for Compose Multiplatform with Kotlin 2.x
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.detekt)
    alias(libs.plugins.android.application)
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
}

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    androidTarget()
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
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
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

        // Shared mobile source set for iOS + Android
        val mobileMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
            }
        }

        // Use default Kotlin hierarchy template for intermediate source sets
        // Default hierarchy automatically creates appleMain for iOS + macOS targets
        // See: https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
        val appleMain by getting {
            dependencies {
                implementation(compose.ui)
                implementation(libs.sqldelight.driver.native)
                implementation(libs.ktor.client.darwin)
            }
        }

        // Wire iosMain to depend on mobileMain
        val iosMain by getting {
            dependsOn(mobileMain)
        }

        val androidMain by getting {
            dependsOn(mobileMain)
            dependencies {
                implementation(libs.sqldelight.driver.android)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
    }
}

android {
    namespace = "org.srmarlins.mtgpirate"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.srmarlins.mtgpirate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = appVersion
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

            // Include all required runtime modules (from suggestRuntimeModules task)
            modules("java.instrument", "java.management", "java.sql", "jdk.unsupported")

            // ProGuard rules to prevent breaking SQLite and other reflection-based code
            buildTypes.release.proguard {
                configurationFiles.from(project.file("compose-desktop.pro"))
            }

            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icon.png"))
            }
        }
    }
}

sqldelight {
    databases {
        create("MtgPirateDatabase") {
            packageName.set("org.srmarlins.mtgpirate.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/migrations"))
        }
    }
}

// Workaround: SQLDelight 2.2.1 migration-verify task fails on Windows because the
// worker process does not forward TMP/TMPDIR env vars, causing xerial sqlite-jdbc to
// attempt writing its native DLL to C:\WINDOWS (AccessDeniedException).
// Upstream fix merged but unreleased: https://github.com/sqldelight/sqldelight/issues/5312
// Safe to disable: this project has no .sqm migration files.
tasks.matching { it.name.contains("verifyCommonMain") && it.name.contains("Migration") }.configureEach {
    enabled = false
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}

// detekt 2.0 KMP: the lifecycle `detekt` task is empty; wire it to main source sets.
// Also declare explicit baseline dependencies to satisfy Gradle configuration cache.
tasks.named("detekt") {
    dependsOn(tasks.matching {
        it.name.startsWith("detekt") && it.name.endsWith("SourceSet") && it.name.contains("Main")
    })
}
tasks.matching { it.name.matches(Regex("detekt\\w+SourceSet")) && !it.name.contains("Baseline") }.configureEach {
    val baselineTaskName = name.replace("detekt", "detektBaseline")
    if (tasks.names.contains(baselineTaskName)) {
        mustRunAfter(baselineTaskName)
    }
}
