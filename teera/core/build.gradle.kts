import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// Targets 17 rather than using a toolchain so the same artifact drops straight into the
// Android app module, whatever JDK the machine happens to have.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // The preview renders with the same typeface the app ships.
    systemProperty(
        "teera.font",
        rootProject.file("app/src/main/res/font/amiri_regular.ttf").absolutePath,
    )
    testLogging { events("passed", "failed", "skipped") }
}
