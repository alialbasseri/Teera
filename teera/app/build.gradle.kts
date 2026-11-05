plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.teera.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.teera.app"
        // 26: the room server a phone runs to host on the Wi-Fi needs java.time and NIO.
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "0.5.0"

        // The internet server, once one is hosted: ./gradlew assembleDebug -Pteera.server=wss://host/ws
        // Left empty, the app says plainly that internet play needs a server and offers Wi-Fi.
        buildConfigField("String", "SERVER_URL", "\"${project.findProperty("teera.server") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Lets the Compose screens be rendered and tapped in a plain JVM test, so an
    // interaction bug is caught here rather than on somebody's phone.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/io.netty.versions.properties",
            "/META-INF/DEPENDENCIES",
        )
    }
}

// Robolectric fetches its Android runtime itself, which a sandboxed build cannot always
// reach. Point it at a jar that is already on disk when one has been put there.
tasks.withType<Test>().configureEach {
    val offline = File(System.getProperty("user.home"), "robolectric-deps")
    if (offline.isDirectory) {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", offline.absolutePath)
    }
}

dependencies {
    implementation(project(":core"))
    // The same room server the cloud runs, so a phone can host a table on the Wi-Fi.
    implementation(project(":room"))
    implementation("io.ktor:ktor-server-cio-jvm:2.3.12")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
