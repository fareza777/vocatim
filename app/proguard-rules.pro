# JNI: whisper native methods are resolved by name at runtime — keep them.
-keep class com.vocatim.whisper.WhisperLib { *; }
-keep class com.vocatim.whisper.WhisperLib$Companion { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
