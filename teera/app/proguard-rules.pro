# kotlinx.serialization keeps its generated serializers on the companion.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.teera.core.**$$serializer { *; }
-keepclassmembers class com.teera.core.** {
    *** Companion;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# The room server a phone runs to host on the Wi-Fi. Ktor looks for an attached IntelliJ
# debugger through java.lang.management, which Android does not have and never reaches.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.impl.**
-keep class io.ktor.server.cio.** { *; }
-keep class io.ktor.server.engine.** { *; }
-keep class io.ktor.server.websocket.** { *; }
-keep class io.ktor.websocket.** { *; }
