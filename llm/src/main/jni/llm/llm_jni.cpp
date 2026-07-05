// Minimal llama.cpp JNI wrapper for on-device summarization.
// Uses only the core llama.h API (no common/jinja layer): load a GGUF model,
// run a ChatML prompt to completion at low temperature, return the text.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <atomic>
#include "llama.h"

#define TAG "VocatimLLM"
#define LOGi(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGe(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {
llama_model   *g_model   = nullptr;
llama_context *g_ctx     = nullptr;
int            g_n_ctx   = 4096;
std::atomic<bool> g_cancel{false};

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
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_loadModel(
        JNIEnv *env, jobject, jstring jpath, jint nThreads, jint nCtx) {
    llama_backend_init();

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) {
        LOGe("Failed to load model");
        return JNI_FALSE;
    }

    g_n_ctx = nCtx;
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = nCtx;
    cparams.n_batch = 512;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        LOGe("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    LOGi("Model loaded (n_ctx=%d, threads=%d)", nCtx, nThreads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_requestCancel(JNIEnv *, jobject) {
    g_cancel.store(true);
}

// Runs one full completion for [jprompt] and returns the generated text.
// The prompt must already be ChatML-formatted by the caller.
extern "C" JNIEXPORT jstring JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_complete(
        JNIEnv *env, jobject, jstring jprompt, jint maxTokens) {
    if (!g_ctx || !g_model) return env->NewStringUTF("");
    g_cancel.store(false);

    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::vector<llama_token> tokens = tokenize(vocab, std::string(prompt), true);
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Each summarize call is independent: wipe the KV cache first.
    llama_memory_clear(llama_get_memory(g_ctx), true);

    if (tokens.empty() || (int) tokens.size() >= g_n_ctx - maxTokens) {
        LOGe("Prompt too long (%d tokens) for context %d", (int) tokens.size(), g_n_ctx);
        return env->NewStringUTF("");
    }

    // Low-temperature chain: coherent, near-deterministic summaries.
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_get_one(tokens.data(), (int) tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        LOGe("Prompt decode failed");
        llama_sampler_free(smpl);
        return env->NewStringUTF("");
    }

    std::string result;
    llama_token new_token = 0;
    for (int i = 0; i < maxTokens; i++) {
        if (g_cancel.load()) break;
        new_token = llama_sampler_sample(smpl, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) break;
        result += piece(vocab, new_token);
        batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, batch) != 0) {
            LOGe("Token decode failed at %d", i);
            break;
        }
    }

    llama_sampler_free(smpl);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_vocatim_llm_LlamaLib_00024Companion_freeModel(JNIEnv *, jobject) {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    llama_backend_free();
}
