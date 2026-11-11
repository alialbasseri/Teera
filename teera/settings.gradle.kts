pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "teera"
include(":core")
include(":room")
include(":server")
// Teera for Windows. The server's Docker image sets TEERA_SERVER_ONLY and leaves it out.
if (System.getenv("TEERA_SERVER_ONLY") != "1") include(":desktop")

// The Android app needs an SDK to even configure. Leave it out where there is none, so
// the server can be built on a plain machine - a Docker image, a cloud build - from the
// same checkout.
val hasAndroidSdk = !System.getenv("ANDROID_HOME").isNullOrBlank() ||
    !System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
if (hasAndroidSdk) include(":app")
