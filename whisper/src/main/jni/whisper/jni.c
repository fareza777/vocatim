// Adapted from whisper.cpp examples/whisper.android jni.c (v1.9.1).
// Vocatim changes: package renamed, model loading is file-path only,
// fullTranscribe takes language + translate and returns a status code.
#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"
#include "ggml.h"

#define UNUSED(x) (void)(x)
#define TAG "VocatimJNI"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    struct whisper_context *context = NULL;
    const char *model_path_chars = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    context = whisper_init_from_file_with_params(model_path_chars, whisper_context_default_params());
    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path_chars);
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    whisper_free(context);
}

JNIEXPORT jint JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads,
        jstring language_str, jboolean translate, jfloatArray audio_data,
        jstring initial_prompt_str, jint beam_size) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    jfloat *audio_data_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize audio_data_length = (*env)->GetArrayLength(env, audio_data);
    // Held for the whole whisper_full call: params.language keeps the pointer.
    const char *language_chars = (*env)->GetStringUTFChars(env, language_str, NULL);
    // Optional custom-vocabulary prompt; also held for the whole call.
    const char *prompt_chars = NULL;
    if (initial_prompt_str != NULL) {
        prompt_chars = (*env)->GetStringUTFChars(env, initial_prompt_str, NULL);
    }

    // beam_size > 0 selects beam search (more accurate, slower); else greedy.
    struct whisper_full_params params = whisper_full_default_params(
            beam_size > 0 ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = translate;
    params.language         = language_chars;
    params.n_threads        = num_threads;
    params.offset_ms        = 0;
    params.no_context       = true;
    params.single_segment   = false;
    // Anti-hallucination: drop blanks and non-speech tokens on silence/music.
    params.suppress_blank   = true;
    params.suppress_nst     = true;
    // Per-token times enable word-level highlighting during playback.
    params.token_timestamps = true;
    if (beam_size > 0) {
        params.beam_search.beam_size = beam_size;
    }
    if (prompt_chars != NULL && prompt_chars[0] != '\0') {
        params.initial_prompt = prompt_chars;
    }

    whisper_reset_timings(context);

    LOGI("About to run whisper_full (lang=%s, threads=%d, beam=%d, samples=%d)",
         language_chars, num_threads, beam_size, audio_data_length);
    const int result = whisper_full(context, params, audio_data_arr, audio_data_length);
    if (result != 0) {
        LOGW("whisper_full failed with %d", result);
    } else {
        whisper_print_timings(context);
    }

    (*env)->ReleaseStringUTFChars(env, language_str, language_chars);
    if (prompt_chars != NULL) {
        (*env)->ReleaseStringUTFChars(env, initial_prompt_str, prompt_chars);
    }
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_data_arr, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    const char *text = whisper_full_get_segment_text(context, index);
    return (*env)->NewStringUTF(env, text);
}

JNIEXPORT jlong JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getTextSegmentT0(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_get_segment_t0(context, index);
}

JNIEXPORT jlong JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getTextSegmentT1(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_get_segment_t1(context, index);
}

JNIEXPORT jint JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getTokenCount(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint segment) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_n_tokens(context, segment);
}

JNIEXPORT jstring JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getTokenText(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint segment, jint token) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    const char *text = whisper_full_get_token_text(context, segment, token);
    return (*env)->NewStringUTF(env, text);
}

JNIEXPORT jlong JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getTokenT0(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint segment, jint token) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_get_token_data(context, segment, token).t0;
}

JNIEXPORT jlong JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getTokenT1(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint segment, jint token) {
    UNUSED(env);
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    return whisper_full_get_token_data(context, segment, token).t1;
}

JNIEXPORT jstring JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getDetectedLanguage(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    const int lang_id = whisper_full_lang_id(context);
    if (lang_id < 0) return NULL;
    const char *lang = whisper_lang_str(lang_id);
    return lang ? (*env)->NewStringUTF(env, lang) : NULL;
}

JNIEXPORT jstring JNICALL
Java_com_vocatim_whisper_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject thiz) {
    UNUSED(thiz);
    const char *sysinfo = whisper_print_system_info();
    return (*env)->NewStringUTF(env, sysinfo);
}
