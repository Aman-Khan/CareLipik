# LLM-Guided Hybrid (experimental)

Select **LLM-Guided Hybrid** in the recording engine picker. Existing Whisper + MedASR Hybrid remains unchanged. Inference runs offline through existing diarization and Whisper, then Qwen3, then selected English MedASR checks. Native models are released between stages.

LLM-Guided Hybrid now tries **whisper.cpp 1.9.5 with Vulkan** on arm64-v8a, using official multilingual **Small Q8_0** weights. A private `:whisper_vulkan` process contains driver crashes and can be killed on cancellation or timeout. Missing models/runtime, GPU initialization/inference errors, empty output and invalid timestamp/alignment data restart primary transcription with the existing Sherpa CPU engine; partial GPU output is discarded. The original Whisper and Whisper + MedASR modes keep Sherpa. GPU acceleration is experimental and does not establish an accuracy or speed improvement without checking the recording on the device.

## Model and build setup

Install Android SDK NDK **27.2.12479018** and CMake **3.22.1**. Use the configured Gradle JDK. The build prepares checksum-pinned **llama.cpp b11489** and **Khronos OpenCL-Headers v2025.07.22** in ignored `tmp/l`. b11489 replaces b6000 because the older OpenCL backend lacked the Q4_K kernels required by this model. The arm64 bridge tries the iQOO 15 Adreno OpenCL GPU first. Other ABIs keep existing transcription and fall back to confidence verification for this mode.

The Windows Whisper Vulkan build also needs **Visual Studio C++ Build Tools** (discovered using `vswhere`). It uses the NDK's bundled `glslc` and pins Khronos Vulkan/SPIR-V headers to **vulkan-sdk-1.4.363.0**. `:app:prepareWhisperVulkanRuntime` builds a standalone JNI library with static internal GGML libraries; these do not replace Qwen's GGML libraries. Sources and build tools are used only on the development computer, never downloaded during phone inference.

The `carelipik.includeQwen3=true` build also prepares **ggml-small-q8_0.bin** (264,464,607 bytes) from official `ggerganov/whisper.cpp`, pinned revision `5359861c739e955e79d9a303bcbc70fb988958b1`, SHA-256 `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`. First use copies and verifies it into private non-backed-up storage; subsequent use checks its verification stamp. Model binaries remain ignored. `:app:prepareWhisperVulkanModel` installs both optional hybrid models before packaging.

CPU and OpenCL backends are separate packaged native plugins. A link-only OpenCL stub is excluded from the APK; the optional Android `uses-native-library` declaration exposes the phone's public vendor `libOpenCL.so`. Native libraries are extracted for explicit backend loading. Qwen runs in private `:qwen_gpu` / `:qwen_cpu` service processes, allowing the app to stop a crashed or blocked GPU worker and retry on CPU. No vendor driver is redistributed.

Build an APK containing Qwen3:

```powershell
.\gradlew.bat assembleDebug '-Pcarelipik.includeQwen3=true'
```

Alternatively run `.\gradlew.bat :app:prepareQwen3Model` once, then build normally. This downloads and verifies **Qwen3-1.7B-Q4_K_M.gguf** into ignored `app/src/main/assets/models/qwen3/`. Model source: [lmstudio-community/Qwen3-1.7B-GGUF](https://huggingface.co/lmstudio-community/Qwen3-1.7B-GGUF/tree/e5e31bf4d96de5da2ce124fa86673f0be7c82346). Revision and SHA-256 are pinned in `gradle/offline-models.gradle.kts`.

The GGUF is about 1.28 GB. First use copies it to private, non-backed-up app storage and validates its checksum. A private verification stamp avoids repeated full-file hashing when its size and modification time remain unchanged. Allow storage for both the APK asset and this copy. Compiled OpenCL kernels are cached in private code-cache storage. After build inputs are cached, `--offline` builds are supported. Without the GGUF, the mode displays a notice and uses the existing Whisper uncertainty/MedASR path. Model binaries must never be committed.

## Review behavior

In this experimental mode, Whisper transcribes chronological audio before diarization. It does not crop audio to speaker turns. Audio windows prefer a quiet boundary between 20 and 25 seconds; if no quiet boundary exists they use the bounded cutoff without dropping samples. After the Whisper worker/recognizer is released, existing Pyannote/TitaNet diarization processes the original recording with the selected speaker count. Decoder word timestamps, when valid, split text at speaker changes; otherwise segment timestamps are matched to the speaker timeline. Ambiguous or overlapping speech stays `Speaker Unknown` with a review warning. No word timing is estimated from text length. This preserves ASR context but cannot guarantee sentence boundaries or repair erroneous speaker clusters. Existing transcription modes keep their previous behavior.

The Vulkan worker disables FP16 arithmetic, cooperative matrices, dot2, asynchronous transfers and graph optimization, and serializes submissions for conservative Adreno execution. GPU output with invalid encoding, excessive symbols or predominantly non-Latin letters in English mode is rejected and primary transcription restarts with Sherpa CPU. CPU output is checked too; visibly corrupt output is not passed to Qwen as a successful transcript. These checks do not establish clinical accuracy or detect every plausible-looking error.

Invalid Vulkan decoder text also creates a private, non-backed-up quarantine marker (`whisper-vulkan-rejected-1_9_5-fp32-v1`). Later consultations skip Vulkan and try native CPU directly instead of repeating the failed GPU attempt. This does not disable Qwen's independent OpenCL GPU backend. Keep the marker on affected devices until a future Vulkan runtime has been verified for correct text, rather than merely successful GPU execution.

After Vulkan fails or is quarantined, this mode tries the same whisper.cpp Small Q8_0 model on CPU/NEON with native word timestamps and the multilingual Small cross-attention DTW alignment preset. Valid token alignment points locate a word on the diarization timeline; words without an alignment point use validated native token bounds. Its private worker is released before diarization and Qwen. Missing runtime/model or invalid native CPU output falls back to existing Sherpa CPU. Sherpa's coarse segments spanning multiple speakers remain `Speaker Unknown` rather than attributing an entire multi-speaker segment to its majority speaker. Word times are used only when byte/text alignment, coverage and monotonic bounds validate; no text-length-based timing is invented.

Qwen windows span multiple short transcript segments, with at most 48 words and eight segments and a four-word overlap. Each segment retains its own IDs, speaker, timestamps and confidence; corrections cannot cross segment boundaries. The selected language is passed explicitly, including an English-only policy. Entered patient name/reference (100 characters), age (12), visit reason (400), speaker count and confirmed doctor identity are supplied as read-only consultation context. Unassigned speaker roles stay unknown; context cannot insert missing speech, alter age/dose numbers or invent diagnoses. Read-only neighboring context cannot supply missing clinical facts. Unsupported corrections must be omitted. Validation rejects changes to numeric digit sequences and recognized negation words, and English mode rejects non-ASCII-letter suggestions. These safeguards reduce risk; they cannot guarantee absence of hallucinations.

The native grammar allows at most two corrections and four word IDs per correction. Generation stops at the complete JSON object without further prose or a separate formatting pass. Stop notices identify analysis-budget, worker-response and native-time limits when identifiable.

Qwen processes the transcript in overlapping 48-word windows (step 44) with neighboring context, speaker IDs, existing word IDs, timestamps and confidence. Each window has a 4,096-token context, at most 384 output tokens and a 45-second native deadline. The context and matching prompt-prefix KV cache are reused across windows. Thinking is disabled. The host watchdog allows 100 seconds for worker model loading and 60 seconds for generation, then kills a blocked worker; GPU failure retries the same window on CPU. The complete Qwen stage has a five-minute budget, including preparation/loading/fallback. The existing Cancel action stops its worker immediately. Failed/incomplete windows fall back to confidence verification; previously validated findings are retained.

Qwen flags and Whisper uncertainty are deduplicated by decoding region. Existing audio extraction, language checks and budgets remain in force: at most four MedASR calls and 25% of the recording. Hindi/Hinglish suggestions remain visible as unverified when no English-compatible audio can be checked.

The review retains original Whisper text, Qwen suggestion, MedASR alternative, reasons, speaker, timestamps, word IDs and agreement status. Phrase-level agreement requires exact aligned replacements or matching full contextual text; otherwise it is inconclusive. A safely aligned differing MedASR phrase has its own acceptance button. Nothing is automatically applied. The doctor can accept, keep the original, replay audio or use the existing transcript editor.

## Live diagnostics

The processing screen shows stage details, model-loading percentages, backend, window counts, prompt evaluation and JSON token progress, plus elapsed time and time since the latest update. Review notices also identify the Qwen backend. During Whisper, details distinguish model loading, speaker diarization and audio recognition. MedASR reports only selected region checks.

Watch both app and worker processes (do not filter by just the main app PID):

Whisper GPU tags are `CareLipikWhisperGpu` (device/progress) and `CareLipikWhisperNative` (runtime details). A review notice identifies Vulkan GPU or Sherpa CPU fallback. Vulkan native inference has a 120-second deadline per audio window; the IPC watchdog allows 120 seconds for loading and 130 seconds per decode. Audio crosses processes using transient read-only shared memory, never additional recording files. Token byte fragments are joined before UTF-8 decoding, and confidence is retained only when token bytes match the segment text. GPU resources are released before Qwen starts.

```powershell
adb logcat -v threadtime 'CareLipikTranscription:I' 'CareLipikQwen:I' 'CareLipikLlama:D' 'CareLipikQwenResult:D' '*:S'
```

Default logs contain processing metadata and failure details. To inspect actual Whisper text, Qwen JSON and MedASR alternatives in a debug APK, explicitly enable content logging:

```powershell
adb shell run-as com.carelipik.app touch no_backup/qwen-debug-content.enabled
```

Disable it again:

```powershell
adb shell run-as com.carelipik.app rm no_backup/qwen-debug-content.enabled
```

The content flag has no effect in release builds. No raw consultation content is logged by these diagnostics without this debug opt-in.

Upstream runtime license: [llama.cpp MIT license](https://github.com/ggml-org/llama.cpp/blob/b11489/LICENSE). Qwen3 model license: Apache-2.0; see the pinned model repository. Runtime and Khronos headers license notices are included in APK assets.
