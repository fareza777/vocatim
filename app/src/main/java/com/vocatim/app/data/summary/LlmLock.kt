package com.vocatim.app.data.summary

import kotlinx.coroutines.sync.Mutex

/**
 * The llama JNI holds ONE global model/context; concurrent use from two
 * engines (e.g. a summary job and an Ask-AI question) would corrupt state.
 * Every local-LLM run must hold this lock from load to release.
 */
object LlmLock {
    val mutex = Mutex()
}
