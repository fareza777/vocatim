// Minimal llama.cpp JNI wrapper for on-device summarization.
// Uses only the core llama.h API (no common/jinja layer): load a GGUF model,
// run a chat-formatted prompt to completion, return (and stream) the text.
#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <unistd.h>
#include <string>
#include <vector>
#include <atomic>
#include <algorithm>
#include "llama.h"
#include "ggml.h"

#define TAG "VocatimLLM"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
llama_model   *g_model   = nullptr;
llama_context *g_ctx     = nullptr;
int            g_n_ctx   = 4096;
const int      g_n_batch = 1024;
std::atomic<bool> g_cancel{false};
std::string    g_diag_path;

// Append one diagnostic line and flush + fsync, so it survives a hard crash
// on the very next native call.
void diag(const char *msg) {
    LOGi("%s", msg);
    if (g_diag_path.empty()) return;
    FILE *f = fopen(g_diag_path.c_str(), "a");
    if (f) {
        fprintf(f, "native: %s\n", msg);
        fflush(f);
        fsync(fileno(f));
        fclose(f);
    }
}

std::vector<llama_token> tokenize(const llama_vocab *vocab, const std::string &text, bool add_special) {
    int n = -llama_tokenize(vocab, text.c_str(), (int) text.size(), nullptr, 0, add_special, true);
    std::vector<llama_token> tokens(n);
    if (llama_tokenize(vocab, text.c_str(), (int) text.size(), tokens.data(), n, add_special, true) < 0) {
        return {};
    }
    return tokens;
}

std::string piece(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (n < 0) return "";
    return std::string(buf, n);
}

// Length of the longest prefix of [s] that ends on a complete UTF-8 sequence.
// Token pieces can split multi-byte characters; anything past this length has
// to wait for the next token before it can be surfaced to Java.
size_t utf8_complete_prefix(const std::string &s) {
    size_t len = s.size();
    size_t i = len;
    // Walk back over at most 3 continuation bytes to the last start byte.
    while (i > 0 && (static_cast<unsigned char>(s[i - 1]) & 0xC0) == 0x80 && len - i < 3) {
        i--;
    }
    if (i == 0) return len; // malformed or empty; pass through as-is
    unsigned char start = static_cast<unsigned char>(s[i - 1]);
    size_t expected =
        (start & 0x80) == 0x00 ? 1 :
        (start & 0xE0) == 0xC0 ? 2 :
        (start & 0xF0) == 0xE0 ? 3 :
        (start & 0xF8) == 0xF0 ? 4 : 1;
    size_t have = len - (i - 1);
    return have < expected ? i - 1 : len;
}

// Delivers only complete UTF-8 bytes to the Java sink, buffering any split
// trailing sequence until the next token completes it.
class TokenSink {
public:
    TokenSink(JNIEnv *env, jobject sink) : env_(env), sink_(sink) {
        if (sink_) {
            jclass cls = env_->GetObjectClass(sink_);
            method_ = env_->GetMethodID(cls, "onText", "([B)V");
            env_->DeleteLocalRef(cls);
        }
    }

    void feed(const std::string &piece) {
        if (!sink_ || !method_) return;
        pending_ += piece;
        size_t emit = utf8_complete_prefix(pending_);
        if (emit == 0) return;
        jbyteArray bytes = env_->NewByteArray((jsize) emit);
        if (!bytes) return;
        env_->SetByteArrayRegion(bytes, 0, (jsize) emit,
                                 reinterpret_cast<const jbyte *>(pending_.data()));
        env_->CallVoidMethod(sink_, method_, bytes);
        env_->DeleteLocalRef(bytes);
        if (env_->ExceptionCheck()) env_->ExceptionClear();
        pending_.erase(0, emit);
    }

private:
    JNIEnv *env_;
    jobject sink_;
    jmethodID method_ = nullptr;
    std::string pending_;
};
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_setDiagFile(
        JNIEnv *env, jobject, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    g_diag_path = path;
    env->ReleaseStringUTFChars(jpath, path);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_loadModel(
        JNIEnv *env, jobject, jstring jpath, jint nThreads, jint nCtx) {
    diag("loadModel:backend_init");
    llama_backend_init();

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    diag("loadModel:load_from_file:start");
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) {
        diag("loadModel:model_null");
        return JNI_FALSE;
    }
    diag("loadModel:model_ok");

    g_n_ctx = nCtx;
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = g_n_batch;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    // Flash attention + quantized KV halves cache memory, which is what makes
    // long contexts affordable on phones.
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
    cparams.type_k = GGML_TYPE_Q8_0;
    cparams.type_v = GGML_TYPE_Q8_0;
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        // Some architectures can't run quantized-KV flash attention; fall
        // back to the default f16 cache rather than failing the load.
        diag("loadModel:fa_q8_failed:fallback_f16");
        cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
        cparams.type_k = GGML_TYPE_F16;
        cparams.type_v = GGML_TYPE_F16;
        g_ctx = llama_init_from_model(g_model, cparams);
    }
    if (!g_ctx) {
        diag("loadModel:context_null");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    diag("loadModel:context_ok");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_requestCancel(JNIEnv *, jobject) {
    g_cancel.store(true);
}

// Runs one full completion for [jprompt] and returns the generated text,
// streaming UTF-8-safe increments to [jsink] (nullable) along the way.
// The prompt must already be chat-formatted by the caller.
extern "C" JNIEXPORT jstring JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_complete(
        JNIEnv *env, jobject, jstring jprompt, jint maxTokens,
        jfloat temperature, jfloat repeatPenalty, jobject jsink) {
    if (!g_ctx || !g_model) return env->NewStringUTF("");
    g_cancel.store(false);

    diag("complete:start");
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::vector<llama_token> tokens = tokenize(vocab, std::string(prompt), true);
    env->ReleaseStringUTFChars(jprompt, prompt);
    diag((std::string("complete:tokenized n=") + std::to_string(tokens.size())).c_str());

    // Each summarize call is independent: wipe the KV cache first.
    llama_memory_clear(llama_get_memory(g_ctx), true);
    diag("complete:kv_cleared");

    if (tokens.empty() || (int) tokens.size() >= g_n_ctx - maxTokens) {
        diag("complete:prompt_too_long");
        return env->NewStringUTF("");
    }

    // Repetition penalty keeps small models from looping on one bullet; the
    // low temperature + min-p tail cut keep the summary factual without the
    // degenerate repetition a pure-greedy chain produces.
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            /* last_n */ 256, /* repeat */ repeatPenalty, /* freq */ 0.0f, /* present */ 0.0f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_min_p(0.05f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    diag("complete:sampler_ready");
    // A single llama_decode may not exceed n_batch tokens, so feed the prompt
    // in n_batch-sized chunks. llama_batch_get_one continues positions from
    // the KV cache automatically across calls.
    diag("complete:prompt_decode:start");
    for (int offset = 0; offset < (int) tokens.size(); offset += g_n_batch) {
        if (g_cancel.load()) {
            llama_sampler_free(smpl);
            return env->NewStringUTF("");
        }
        const int chunk = std::min(g_n_batch, (int) tokens.size() - offset);
        llama_batch pb = llama_batch_get_one(tokens.data() + offset, chunk);
        if (llama_decode(g_ctx, pb) != 0) {
            diag("complete:prompt_decode:fail");
            llama_sampler_free(smpl);
            return env->NewStringUTF("");
        }
    }
    diag("complete:prompt_decode:ok");

    TokenSink sink(env, jsink);
    std::string result;
    llama_token new_token = 0;
    for (int i = 0; i < maxTokens; i++) {
        if (g_cancel.load()) break;
        new_token = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) break;
        const std::string text = piece(vocab, new_token);
        result += text;
        sink.feed(text);
        llama_batch gb = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, gb) != 0) {
            diag("complete:gen_decode:fail");
            break;
        }
        if (i == 0) diag("complete:first_token_ok");
    }

    diag((std::string("complete:done chars=") + std::to_string(result.size())).c_str());
    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_freeModel(JNIEnv *, jobject) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_free();
}
