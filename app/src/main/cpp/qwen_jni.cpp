#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#ifdef CARELIPIK_ARM64
#include "llama.h"
#include "ggml-backend.h"

namespace {
struct Session {
    JavaVM *vm = nullptr;
    jobject checker = nullptr;
    jmethodID check = nullptr;
    jmethodID report = nullptr;
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    std::vector<llama_token> previous_prompt;
    std::vector<ggml_backend_dev_t> devices;
    int load_percent = -10;
    std::atomic<bool> aborted{false};
    std::chrono::steady_clock::time_point deadline;
};

bool abort_inference(void *opaque) {
    auto *s = static_cast<Session *>(opaque);
    if (s->aborted || std::chrono::steady_clock::now() > s->deadline) return true;
    JNIEnv *env = nullptr;
    bool attached = s->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK;
    if (attached && s->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return true;
    env->CallVoidMethod(s->checker, s->check);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        s->aborted = true;
    }
    if (attached) s->vm->DetachCurrentThread();
    return s->aborted;
}

void report(Session *s, const std::string &message) {
    __android_log_print(ANDROID_LOG_INFO, "CareLipikQwen", "%s", message.c_str());
    JNIEnv *env = nullptr;
    bool attached = s->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK;
    if (attached && s->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
    auto text = env->NewStringUTF(message.c_str());
    env->CallVoidMethod(s->checker, s->report, text);
    env->DeleteLocalRef(text);
    if (env->ExceptionCheck()) { env->ExceptionClear(); s->aborted = true; }
    if (attached) s->vm->DetachCurrentThread();
}
bool load_progress(float fraction, void *opaque) {
    auto *s = static_cast<Session *>(opaque);
    int percent = static_cast<int>(fraction * 100);
    if (percent >= s->load_percent + 10) {
        s->load_percent = percent;
        report(s, "Qwen model loading: " + std::to_string(percent) + "%");
    }
    return !abort_inference(opaque);
}
void check_session(Session *s) {
    if (abort_inference(s)) throw std::runtime_error("Local Qwen3 inference cancelled or reached its time limit.");
}

// Constrained JSON is still validated against the exact supplied IDs and source text in Kotlin.
const char *grammar = R"GBNF(
root ::= "{" ws "\"corrections\"" ws ":" ws "[" ws (item (ws "," ws item)?)? ws "]" ws "}"
item ::= "{" ws "\"segment_id\"" ws ":" ws string ws "," ws "\"word_ids\"" ws ":" ws "[" ws string (ws "," ws string)? (ws "," ws string)? (ws "," ws string)? ws "]" ws "," ws "\"original\"" ws ":" ws string ws "," ws "\"suggestion\"" ws ":" ws string ws "," ws "\"reason\"" ws ":" ws string ws "," ws "\"priority\"" ws ":" ws ("\"low\"" | "\"medium\"" | "\"high\"") ws "}"
string ::= "\"" ([^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4}))* "\""
ws ::= [ \t\n\r]*
)GBNF";
const char *report_grammar = R"GBNF(
root ::= "{" ws "\"assignments\"" ws ":" ws "[" ws (item (ws "," ws item){0,3})? ws "]" ws "}"
item ::= "{" ws "\"section_id\"" ws ":" ws string ws "," ws "\"source_ids\"" ws ":" ws "[" ws string (ws "," ws string){0,3} ws "]" ws "}"
string ::= "\"" ([^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4}))* "\""
ws ::= [ \t\n\r]*
)GBNF";
}
#endif

extern "C" JNIEXPORT jlong JNICALL
Java_com_carelipik_app_data_transcription_NativeQwen3_open(JNIEnv *env, jobject, jstring path,
        jstring directory, jboolean gpu, jobject checker) {
#ifdef CARELIPIK_ARM64
    auto session = std::make_unique<Session>();
    env->GetJavaVM(&session->vm);
    session->checker = env->NewGlobalRef(checker);
    jclass cls = env->GetObjectClass(checker);
    session->check = env->GetMethodID(cls, "check", "()V");
    session->report = env->GetMethodID(cls, "report", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    session->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(90);
    try {
        static std::once_flag initialized;
        const char *dir_chars = env->GetStringUTFChars(directory, nullptr);
        std::string backend_directory(dir_chars);
        env->ReleaseStringUTFChars(directory, dir_chars);
        std::call_once(initialized, [&] {
            llama_log_set([](enum ggml_log_level level, const char *text, void *) {
                __android_log_print(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG,
                    "CareLipikLlama", "%s", text);
            }, nullptr);
            report(session.get(), "Qwen: initializing CPU backend");
            if (!ggml_backend_load((backend_directory + "/libggml-cpu.so").c_str()))
                throw std::runtime_error("Could not load packaged CPU backend");
            if (gpu) {
                report(session.get(), "Qwen: initializing Adreno OpenCL driver");
                if (!ggml_backend_load((backend_directory + "/libggml-opencl.so").c_str()))
                    throw std::runtime_error("Could not load Adreno OpenCL backend or vendor driver");
            }
            llama_backend_init();
        });
        const char *chars = env->GetStringUTFChars(path, nullptr);
        std::string model_path(chars);
        env->ReleaseStringUTFChars(path, chars);
        auto params = llama_model_default_params();
        params.n_gpu_layers = gpu ? 99 : 0;
        if (gpu) {
            auto device = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_GPU);
            if (!device) throw std::runtime_error("OpenCL backend found no GPU device");
            session->devices = {device, nullptr};
            params.devices = session->devices.data();
            report(session.get(), std::string("Qwen backend: Adreno GPU / ") + ggml_backend_dev_description(device));
        } else report(session.get(), "Qwen backend: CPU fallback");
        params.progress_callback = load_progress;
        params.progress_callback_user_data = session.get();
        session->model = llama_model_load_from_file(model_path.c_str(), params);
        check_session(session.get());
        if (!session->model) throw std::runtime_error("Could not load local Qwen3 GGUF model.");
        report(session.get(), "Qwen model loaded; ready for transcript analysis");
        return reinterpret_cast<jlong>(session.release());
    } catch (const std::exception &e) {
        if (session->model) llama_model_free(session->model);
        env->DeleteGlobalRef(session->checker);
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
        return 0;
    }
#else
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Local Qwen3 requires arm64-v8a.");
    return 0;
#endif
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_carelipik_app_data_transcription_NativeQwen3_generate(JNIEnv *env, jobject, jlong handle, jbyteArray prompt_bytes, jboolean report_mode) {
#ifdef CARELIPIK_ARM64
    auto *s = reinterpret_cast<Session *>(handle);
    try {
        if (!s) throw std::runtime_error("Local Qwen3 session is closed.");
        s->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(45);
        check_session(s);
        std::string prompt(env->GetArrayLength(prompt_bytes), '\0');
        env->GetByteArrayRegion(prompt_bytes, 0, prompt.size(), reinterpret_cast<jbyte *>(prompt.data()));
        const auto *vocab = llama_model_get_vocab(s->model);
        int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
        constexpr int context_tokens = 4096;
        constexpr int output_tokens = 384;
        if (count <= 0 || count + output_tokens + 16 > context_tokens)
            throw std::runtime_error("Qwen3 contextual window exceeds the token limit.");
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true) < 0)
            throw std::runtime_error("Could not tokenize the local contextual window.");
        auto params = llama_context_default_params();
        params.n_ctx = context_tokens;
        params.n_batch = 256;
        params.n_ubatch = 128;
        params.n_threads = 4;
        params.n_threads_batch = 4;
        params.abort_callback = abort_inference;
        params.abort_callback_data = s;
        if (!s->context) {
            report(s, "Qwen: allocating inference context");
            s->context = llama_init_from_model(s->model, params);
        }
        auto *context = s->context;
        if (!context) throw std::runtime_error("Could not allocate local Qwen3 context.");
        int prefix = 0;
        while (prefix < count - 1 && prefix < static_cast<int>(s->previous_prompt.size()) &&
               tokens[prefix] == s->previous_prompt[prefix]) ++prefix;
        if (!llama_memory_seq_rm(llama_get_memory(context), 0, prefix, -1)) {
            llama_memory_clear(llama_get_memory(context), true);
            prefix = 0;
        }
        s->previous_prompt = tokens;
        report(s, "Qwen prompt: " + std::to_string(count) + " tokens, " + std::to_string(prefix) + " cached");
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
            llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
        auto *constraint = llama_sampler_init_grammar(vocab, report_mode ? report_grammar : grammar, "root");
        if (!constraint) throw std::runtime_error("Could not initialize JSON grammar.");
        llama_sampler_chain_add(sampler.get(), constraint);
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
        for (int start = prefix; start < count; start += 256) {
            check_session(s);
            auto batch = llama_batch_get_one(tokens.data() + start, std::min(256, count - start));
            if (llama_decode(context, batch) != 0) throw std::runtime_error("Qwen3 prompt decoding failed.");
            report(s, "Qwen prompt evaluation: " + std::to_string(std::min(start + 256, count)) + "/" + std::to_string(count) + " tokens");
        }
        std::string output;
        bool ended = false;
        int object_depth = 0;
        bool in_string = false, escaped = false, object_started = false;
        for (int i = 0; i < output_tokens; ++i) {
            check_session(s);
            auto token = llama_sampler_sample(sampler.get(), context, -1);
            if (llama_vocab_is_eog(vocab, token)) { ended = true; break; }
            char piece[512];
            int size = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
            if (size < 0) throw std::runtime_error("Invalid local Qwen3 output token.");
            output.append(piece, size);
            // Stop at the complete grammar-constrained object, without generating
            // whitespace or waiting for an additional end-of-generation token.
            for (int j = 0; j < size; ++j) {
                const char ch = piece[j];
                if (in_string) {
                    if (escaped) escaped = false;
                    else if (ch == '\\') escaped = true;
                    else if (ch == '"') in_string = false;
                } else if (ch == '"') in_string = true;
                else if (ch == '{') { ++object_depth; object_started = true; }
                else if (ch == '}') --object_depth;
            }
            if (object_started && object_depth == 0 && !in_string) { ended = true; break; }
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(context, batch) != 0) throw std::runtime_error("Qwen3 generation failed.");
            if (i % 16 == 0) report(s, "Qwen generating JSON: " + std::to_string(i + 1) + "/384 tokens");
        }
        check_session(s);
        if (!ended) throw std::runtime_error("Qwen3 reached the output limit; incomplete suggestions were discarded.");
        report(s, "Qwen JSON generation complete: " + std::to_string(output.size()) + " bytes");
        auto result = env->NewByteArray(output.size());
        env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception &e) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), e.what());
        return nullptr;
    }
#else
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Local Qwen3 requires arm64-v8a.");
    return nullptr;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_carelipik_app_data_transcription_NativeQwen3_close(JNIEnv *env, jobject, jlong handle) {
#ifdef CARELIPIK_ARM64
    auto *s = reinterpret_cast<Session *>(handle);
    if (s) {
        if (s->context) llama_free(s->context);
        llama_model_free(s->model);
        env->DeleteGlobalRef(s->checker);
        delete s;
    }
#endif
}
