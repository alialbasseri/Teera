import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Teera for Windows (and any desktop): Compose for Desktop on the JVM.
 *
 * The screens, the view model and the game clients are the Android app's own files,
 * compiled here as they are; this module only adds what a computer does differently
 * (src/main/kotlin: the window, sound, settings, files). A change to the phone's screens
 * reaches the desktop with the next build.
 */
plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose") version "1.7.3"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

/** The phone's sources that are the same on a computer. */
val shared = rootProject.file("app/src/main/kotlin")
val phoneOnly = listOf(
    "com/teera/app/MainActivity.kt",
    "com/teera/app/Platform.kt",
    "com/teera/app/Push.kt",
    "com/teera/app/ScreenshotWatch.kt",
    "com/teera/app/net/Lan.kt",
    "com/teera/app/voice/VoiceChat.kt",
    "com/teera/app/sound/SoundEngine.kt",
)

// The desktop's own files are named apart from these (DesktopSound.kt, not SoundEngine.kt),
// so leaving the phone's out by path cannot catch them.
sourceSets.main {
    kotlin.srcDir(shared)
    kotlin.exclude(phoneOnly)
}

// The phone's pictures and fonts, taken from where the phone keeps them (and their
// licences with them). Only the sounds are the desktop's own copy: Java plays WAV, not Ogg.
tasks.processResources {
    from(rootProject.file("app/src/main/res/drawable-nodpi")) { into("faces"); include("face_*.png") }
    from(rootProject.file("app/src/main/res/font")) { into("font"); include("el_messiri_*.ttf", "aref_ruqaa_*.ttf") }
    from(rootProject.file("app/src/main/assets/licenses")) { into("licenses") }
}

dependencies {
    implementation(project(":core"))
    // The room server, so a computer can host a table on the Wi-Fi like a phone can.
    implementation(project(":room"))
    implementation("io.ktor:ktor-server-cio-jvm:2.3.12")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.slf4j:slf4j-nop:2.0.13")
    // Finding tables on the Wi-Fi, the way phones announce them (DNS-SD).
    implementation("org.jmdns:jmdns:3.5.9")

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // No friends list, invitations or tutorial in tests: nothing reaches the real server.
    systemProperty("teera.noPresence", "true")
    systemProperty("teera.live", (project.findProperty("teera.live") != null).toString())
    systemProperty("teera.icon", project.findProperty("teera.icon")?.toString() ?: "")
    systemProperty("teera.res", rootProject.file("app/src/main/res").absolutePath)
    systemProperty("teera.shots", layout.buildDirectory.dir("shots").get().asFile.absolutePath)
    testLogging { events("passed", "failed", "skipped"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

/** The Windows build number the server books this app under (see Hello.platform). */
val windowsBuild = 2
val windowsVersion = "1.1.0"

val writeAppInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/appinfo")
    val server = (project.findProperty("teera.server") ?: "wss://teera-xfvr.onrender.com/ws").toString()
    inputs.property("build", windowsBuild)
    inputs.property("version", windowsVersion)
    inputs.property("server", server)
    outputs.dir(out)
    doLast {
        val file = out.get().file("com/teera/app/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.teera.app
            |
            |internal object BuildInfo {
            |    const val VERSION_CODE = $windowsBuild
            |    const val VERSION_NAME = "$windowsVersion"
            |    const val SERVER_URL = "$server"
            |}
            |""".trimMargin(),
        )
    }
}
sourceSets.main { kotlin.srcDir(writeAppInfo) }

compose.desktop {
    application {
        mainClass = "com.teera.app.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Dsun.java2d.uiScale.enabled=true")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Teera"
            packageVersion = windowsVersion
            description = "طيرة - دومينا عراقية"
            vendor = "Teera"
            modules("java.instrument", "java.management", "java.naming", "jdk.crypto.ec", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icon/teera.ico"))
                menu = true
                menuGroup = "Teera"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6f0b8a3e-2c41-4d7a-9b1e-5a7c3e9d2f14"
            }
            linux { iconFile.set(project.file("icon/teera.png")) }
        }
    }
}
