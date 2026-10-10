#include <jni.h>
#include <algorithm>
#include <cstdlib>
#include <android/log.h>
#include "whisper.h"
#include "ggml-backend.h"
#include <chrono>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>
#include <stdexcept>

namespace {
struct Session {
    whisper_context *context = nullptr;
    JavaVM *vm = nullptr;
    jobject checker = nullptr;
    jmethodID report = nullptr;
    bool gpu_selected = false;
    bool gpu_failed = false;
    bool gpu = true;
    std::chrono::steady_clock::time_point deadline;
};
void report(Session *s, const std::string &detail) {
    __android_log_print(ANDROID_LOG_INFO, "CareLipikWhisperGpu", "%s", detail.c_str());
    JNIEnv *env = nullptr;
    bool attached = s->vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK;
    if (attached && s->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
    auto text = env->NewStringUTF(detail.c_str());
    env->CallVoidMethod(s->checker, s->report, text);
    env->DeleteLocalRef(text);
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) s->vm->DetachCurrentThread();
}
bool abort_inference(void *opaque) {
    return std::chrono::steady_clock::now() > static_cast<Session *>(opaque)->deadline;
}
std::string quote(const char *text) {
    std::string out = "\"";
    const char *hex = "0123456789abcdef";
    for (const unsigned char *p = reinterpret_cast<const unsigned char *>(text); *p; ++p) {
        if (*p == '"' || *p == '\\') { out += '\\'; out += static_cast<char>(*p); }
        else if (*p < 32) { out += "\\u00"; out += hex[*p >> 4]; out += hex[*p & 15]; }
        else out += static_cast<char>(*p);
    }
    return out + '"';
}
std::string hex_bytes(const char *text) {
    const char *hex = "0123456789abcdef";
    std::string out;
    for (const unsigned char *p = reinterpret_cast<const unsigned char *>(text); *p; ++p) {
        out += hex[*p >> 4]; out += hex[*p & 15];
    }
    return out;
}
void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_carelipik_app_data_transcription_NativeWhisperVulkan_open(JNIEnv *env, jobject, jstring path, jboolean gpu, jobject checker) {
    auto *s = new Session;
    s->gpu = gpu == JNI_TRUE;
    env->GetJavaVM(&s->vm);
    s->checker = env->NewGlobalRef(checker);
    auto cls = env->GetObjectClass(checker);
    s->report = env->GetMethodID(cls, "report", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(cls);
    try {
        // Use conservative Vulkan math and synchronization on the target Adreno.
        // Backend selection alone does not establish numerical correctness.
        setenv("GGML_VK_DISABLE_F16", "1", 1);
        setenv("GGML_VK_DISABLE_COOPMAT", "1", 1);
        setenv("GGML_VK_DISABLE_COOPMAT2", "1", 1);
        setenv("GGML_VK_DISABLE_DOT2", "1", 1);
        setenv("GGML_VK_DISABLE_ASYNC", "1", 1);
        setenv("GGML_VK_DISABLE_GRAPH_OPTIMIZE", "1", 1);
        setenv("GGML_VK_SERIALIZE_SUBMISSIONS", "1", 1);
        const auto logger = [](ggml_log_level level, const char *text, void *opaque) {
            auto *session = static_cast<Session *>(opaque);
            const std::string detail(text);
            if (detail.find("whisper_backend_init_gpu: using ") != std::string::npos) session->gpu_selected = true;
            if (detail.find("whisper_backend_init_gpu: failed to initialize") != std::string::npos) session->gpu_failed = true;
            __android_log_print(level == GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG,
                "CareLipikWhisperNative", "%s", text);
        };
        whisper_log_set(logger, s);
        ggml_log_set(logger, s);
        if (s->gpu) {
            ggml_backend_load_all();
            auto device = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_GPU);
            if (!device) device = ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_IGPU);
            if (!device) throw std::runtime_error("No Vulkan GPU was found");
            report(s, std::string("Whisper Vulkan device: ") + ggml_backend_dev_description(device));
        } else report(s, "Whisper native CPU: NEON word-timestamp decoding");
        auto params = whisper_context_default_params();
        params.use_gpu = s->gpu;
        params.flash_attn = false;
        params.dtw_token_timestamps = !s->gpu;
        if (!s->gpu) params.dtw_aheads_preset = WHISPER_AHEADS_SMALL;
        const char *chars = env->GetStringUTFChars(path, nullptr);
        std::string model_path(chars);
        env->ReleaseStringUTFChars(path, chars);
        report(s, s->gpu ? "Whisper Vulkan: loading Small Q8_0 model" : "Whisper native CPU: loading Small Q8_0 model");
        s->context = whisper_init_from_file_with_params(model_path.c_str(), params);
        if (!s->context) throw std::runtime_error("Could not load Whisper Vulkan model");
        if (s->gpu && (!s->gpu_selected || s->gpu_failed)) throw std::runtime_error("Whisper could not activate the Vulkan GPU backend");
        report(s, s->gpu ? "Whisper Vulkan model loaded" : "Whisper native CPU model loaded");
        return reinterpret_cast<jlong>(s);
    } catch (const std::exception &error) {
        if (s->context) whisper_free(s->context);
        env->DeleteGlobalRef(s->checker);
        delete s;
        fail(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_carelipik_app_data_transcription_NativeWhisperVulkan_recognize(JNIEnv *env, jobject, jlong handle,
        jfloatArray audio, jstring language) {
    auto *s = reinterpret_cast<Session *>(handle);
    try {
        if (!s || !s->context) throw std::runtime_error("Whisper Vulkan session is closed");
        const int count = env->GetArrayLength(audio);
        if (count <= 0 || count > 16000 * 25) throw std::runtime_error("Whisper Vulkan audio window is invalid");
        std::vector<float> samples(count);
        env->GetFloatArrayRegion(audio, 0, count, samples.data());
        const char *chars = env->GetStringUTFChars(language, nullptr);
        std::string code(chars);
        env->ReleaseStringUTFChars(language, chars);
        auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = 4;
        params.language = code.empty() ? "auto" : code.c_str();
        params.translate = false;
        params.no_context = true;
        params.no_timestamps = false;
        params.token_timestamps = true;
        params.suppress_nst = true;
        params.max_len = 100;
        params.split_on_word = true;
        params.print_progress = params.print_realtime = params.print_timestamps = params.print_special = false;
        params.temperature = 0.0f;
        params.temperature_inc = 0.0f;
        params.greedy.best_of = 1;
        params.abort_callback = abort_inference;
        params.abort_callback_user_data = s;
        params.progress_callback = [](whisper_context *, whisper_state *, int percent, void *opaque) {
            auto *session = static_cast<Session *>(opaque);
            report(session, std::string(session->gpu ? "Whisper Vulkan decoding: " : "Whisper native CPU decoding: ") + std::to_string(std::max(0, std::min(100, percent))) + "%");
        };
        params.progress_callback_user_data = s;
        s->deadline = std::chrono::steady_clock::now() + std::chrono::seconds(120);
        report(s, s->gpu ? "Whisper Vulkan: encoding audio and decoding timestamped text" : "Whisper native CPU: encoding audio and decoding word timestamps");
        if (whisper_full(s->context, params, samples.data(), count) != 0)
            throw std::runtime_error("Whisper Vulkan inference failed or reached its time limit");
        std::ostringstream json;
        const char *detected = whisper_lang_str(whisper_full_lang_id(s->context));
        json << "{\"language\":" << quote(detected ? detected : "") << ",\"segments\":[";
        const int segments = whisper_full_n_segments(s->context);
        for (int i = 0; i < segments; ++i) {
            if (i) json << ',';
            json << "{\"text\":" << quote(whisper_full_get_segment_text(s->context, i))
                << ",\"start_ms\":" << whisper_full_get_segment_t0(s->context, i) * 10
                << ",\"end_ms\":" << whisper_full_get_segment_t1(s->context, i) * 10 << ",\"tokens\":[";
            bool first = true;
            for (int j = 0; j < whisper_full_n_tokens(s->context, i); ++j) {
                auto data = whisper_full_get_token_data(s->context, i, j);
                if (data.id >= whisper_token_eot(s->context)) continue;
                if (!first) json << ',';
                first = false;
                const float p = std::isfinite(data.p) ? data.p : 0.0f;
                json << "{\"bytes\":\"" << hex_bytes(whisper_full_get_token_text(s->context, i, j))
                    << "\",\"p\":" << p << ",\"start_ms\":" << data.t0 * 10 << ",\"end_ms\":" << data.t1 * 10
                    << ",\"alignment_ms\":" << (!s->gpu && data.t_dtw >= 0 ? data.t_dtw * 10 : -1) << '}';
            }
            json << "]}";
        }
        json << "]}";
        report(s, std::string(s->gpu ? "Whisper Vulkan decode complete: " : "Whisper native CPU decode complete: ") + std::to_string(segments) + " timestamped segments");
        const auto output = json.str();
        auto result = env->NewByteArray(output.size());
        env->SetByteArrayRegion(result, 0, output.size(), reinterpret_cast<const jbyte *>(output.data()));
        return result;
    } catch (const std::exception &error) { fail(env, error.what()); return nullptr; }
}
