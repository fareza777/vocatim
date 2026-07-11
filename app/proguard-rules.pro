# JNI: whisper native methods are resolved by name at runtime — keep them.
-keep class com.vocatim.whisper.WhisperLib { *; }
-keep class com.vocatim.whisper.WhisperLib$Companion { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# LLM JNI: native methods resolved by name at runtime.
-keep class com.vocatim.llm.LlamaLib { *; }
-keep class com.vocatim.llm.LlamaLib$Companion { *; }

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

# sherpa-onnx (Parakeet engine): JNI resolves these classes and fields by
# name; the vendored local AAR's consumer rules are not always applied.
-keep class com.k2fsa.sherpa.onnx.** { *; }
