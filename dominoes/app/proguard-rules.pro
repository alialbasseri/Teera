# kotlinx.serialization keeps its generated serializers on the companion.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dominoes.core.**$$serializer { *; }
-keepclassmembers class com.dominoes.core.** {
    *** Companion;
}
-dontwarn okhttp3.**
-dontwarn okio.**
