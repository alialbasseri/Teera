import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/*
 * Teera in the browser: the Android app's own screens, view model and game clients,
 * compiled to WebAssembly with Compose Multiplatform, as the iPhone and Windows apps are.
 * Only what a browser does differently lives here (src/wasmJsMain). The server hands it out
 * at /play (see room/WebApp.kt); `gradle :web:publishToServer` copies a fresh build there.
 */
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.7.3"
}

/** The phone's sources that are the same in a browser. */
val shared = rootProject.file("app/src/main/kotlin")
val notOnWeb = listOf(
    "com/teera/app/MainActivity.kt",
    "com/teera/app/Platform.kt",
    "com/teera/app/Push.kt",
    "com/teera/app/ScreenshotWatch.kt",
    "com/teera/app/net/Lan.kt",
    "com/teera/app/voice/VoiceChat.kt",
    "com/teera/app/sound/SoundEngine.kt",
    "com/teera/app/net/OkHttpLine.kt",
    "com/teera/app/net/LanHost.kt",
)

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "teera"
        browser {
            commonWebpackConfig { outputFileName = "teera.js" }
        }
        binaries.executable()
    }

    sourceSets {
        // The phone's files and the browser's own compile as one, as on the iPhone.
        val wasmJsMain by getting {
            kotlin.srcDir(shared)
            kotlin.exclude(notOnWeb)
            dependencies {
                implementation(project(":core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
    }
}

// The phone's pictures and fonts, and the iPhone's AAC sounds (every browser plays them),
// taken from where they live rather than copied.
tasks.named<ProcessResources>("wasmJsProcessResources") {
    from(rootProject.file("app/src/main/res/drawable-nodpi")) { into("faces"); include("face_*.png") }
    from(rootProject.file("app/src/main/res/font")) { into("fonts"); include("el_messiri_*.ttf", "aref_ruqaa_*.ttf") }
    from(rootProject.file("../ios/Teera/GameFiles/Sounds")) { into("sounds") }
    from(rootProject.file("room/src/main/resources/site/icon.png"))
}

/** The built game, into the server's resources, which hand it out at /play. */
tasks.register<Sync>("publishToServer") {
    dependsOn("wasmJsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/wasmJs/productionExecutable")) {
        exclude("*.map", "*.LICENSE.txt")
    }
    into(rootProject.file("room/src/main/resources/play"))
}
