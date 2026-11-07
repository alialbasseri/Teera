import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
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
    implementation(project(":room"))
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    // The books: players, matches and games, kept in Postgres (see DEPLOY.md).
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")
    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:2.2.224")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")
}

application {
    mainClass.set("com.teera.server.MainKt")
}

tasks.test { useJUnitPlatform() }
