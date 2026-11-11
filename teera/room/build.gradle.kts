import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The room server itself, with no engine attached. The cloud server runs it on Netty;
// a phone hosting a table on the Wi-Fi runs the very same code on CIO.
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

val ktorVersion = "2.3.12"

dependencies {
    api(project(":core"))
    api("io.ktor:ktor-server-core-jvm:$ktorVersion")
    api("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("org.slf4j:slf4j-api:2.0.13")
}
