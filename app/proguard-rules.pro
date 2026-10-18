# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.dsh.companion.**$$serializer { *; }
-keepclassmembers class com.dsh.companion.** {
    *** Companion;
}
-keepclasseswithmembers class com.dsh.companion.** {
    kotlinx.serialization.KSerializer serializer(...);
}
