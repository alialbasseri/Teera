import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The rules, the room, the bots and the wire format: one code for the server, the phone,
// the computer and the iPhone. Kotlin Multiplatform, so the same files compile for the JVM
// and, on a Mac, for iOS.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

/** iOS can only be built on a Mac; elsewhere (the server's image, Windows, Linux) it is left out. */
val buildIos = System.getProperty("os.name").startsWith("Mac") && System.getenv("TEERA_SERVER_ONLY") != "1"

kotlin {
    // Targets 17 so the same artifact drops straight into the Android app module.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    if (buildIos) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        jvmTest {
            kotlin.srcDir("src/test/kotlin")
            dependencies { implementation(kotlin("test")) }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    // The preview renders with the same typeface the app ships.
    systemProperty(
        "teera.font",
        rootProject.file("app/src/main/res/font/amiri_regular.ttf").absolutePath,
    )
    testLogging { events("passed", "failed", "skipped") }
}

// `gradle :core:test`, as before the move to multiplatform.
tasks.register("test") { dependsOn("jvmTest") }
