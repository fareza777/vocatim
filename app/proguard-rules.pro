# JNI: whisper native methods are resolved by name at runtime — keep them.
-keep class com.vocatim.whisper.WhisperLib { *; }
-keep class com.vocatim.whisper.WhisperLib$Companion { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Room / Hilt / app models (release minify)
-keep class com.vocatim.app.data.db.** { *; }
-keep class com.vocatim.app.VocatimApp { *; }
-keep @androidx.room.Entity class *
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclassmembers class * {
    @dagger.* <fields>;
    @dagger.* <methods>;
    @javax.inject.* <fields>;
    @javax.inject.* <methods>;
}
-keepclasseswithmembers class * {
    @androidx.room.* <methods>;
}
