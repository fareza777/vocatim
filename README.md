# Vocatim

Offline voice-to-text transcription for Android, powered by [whisper.cpp](https://github.com/ggerganov/whisper.cpp).
100% on-device: no backend, no cloud API, no data leaves the phone.

## Building

whisper.cpp is a git submodule — clone recursively:

```bash
git clone --recursive https://github.com/fareza777/vocatim.git
```

Requirements: JDK 17, Android SDK 35, NDK 27.1, CMake 3.22.

```bash
./gradlew :app:assembleDebug
```

The debug APK targets `arm64-v8a` only.

## Modules

- `:app` — Compose UI, ViewModels, Room, model download manager
- `:whisper` — JNI binding to whisper.cpp (adapted from the official `examples/whisper.android`)

Whisper ggml models (`tiny`, `base`) are downloaded from Hugging Face on first use;
they are not bundled in the APK.
