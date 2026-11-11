/*
 * Teera for iPhone: the Android app's own screens, view model and game clients, compiled
 * for iOS with Compose Multiplatform into a framework (TeeraKit) that the Xcode project in
 * ios/ shows full screen. Only what an iPhone does differently lives here (src/iosMain).
 *
 * iOS can only be compiled on a Mac: settings.gradle.kts includes this module there only.
 */
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.7.3"
}

/** The phone's sources that are the same on an iPhone. */
val shared = rootProject.file("app/src/main/kotlin")
val notOnIos = listOf(
    // The phone's own: Android activity, push, screenshots, sound, voice, Wi-Fi discovery.
    "com/teera/app/MainActivity.kt",
    "com/teera/app/Platform.kt",
    "com/teera/app/Push.kt",
    "com/teera/app/ScreenshotWatch.kt",
    "com/teera/app/net/Lan.kt",
    "com/teera/app/voice/VoiceChat.kt",
    "com/teera/app/sound/SoundEngine.kt",
    // The JVM's own (the phone and the computer): OkHttp, and hosting a table (Ktor server).
    "com/teera/app/net/OkHttpLine.kt",
    "com/teera/app/net/LanHost.kt",
)

val ktorVersion = "2.3.12"

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "TeeraKit"
            isStatic = true
        }
    }

    sourceSets {
        // The phone's files and the iPhone's own compile as one: the shared screens call the
        // iOS answers (IosPlatform.kt) directly, as on the phone they call Platform.kt.
        iosMain {
            kotlin.srcDir(shared)
            kotlin.exclude(notOnIos)
        }
        commonMain {
            dependencies {
                implementation(project(":core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-websockets:$ktorVersion")
                implementation("io.ktor:ktor-client-darwin:$ktorVersion")
            }
        }
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
    }
}
