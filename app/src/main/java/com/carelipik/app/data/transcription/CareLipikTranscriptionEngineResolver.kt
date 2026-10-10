package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.BuildConfig
import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver
import com.carelipik.app.data.voice.SherpaDoctorVoiceRoleMatcher
import com.carelipik.app.data.local.DeviceApiKeyProvider
import com.carelipik.app.domain.repository.ApiProvider

class CareLipikTranscriptionEngineResolver(context: Context) : TranscriptionEngineResolver {
    private val deviceApiKeyProvider = DeviceApiKeyProvider(context.applicationContext)

    // Sherpa's JNI library resolves OrtGetApiBase from libonnxruntime.so at load time.
    // Load the shared runtime first because Apollo Medical-NER also contributes ONNX bindings,
    // which means Android cannot rely on the former transitive load order.
    @Suppress("unused")
    private val sherpaNativeRuntimeLoaded = System.loadLibrary("onnxruntime")

    private val diarizationEngine = SherpaOfflineSpeakerDiarizationEngine(
        context.applicationContext
    )
    private val doctorVoiceRoleMatcher = SherpaDoctorVoiceRoleMatcher(context.applicationContext)
    private val engines: Map<TranscriptionEngineOption, AudioTranscriptionEngine> = listOf(
        SherpaMedAsrTranscriptionEngine(
            context.applicationContext,
            diarizationEngine,
            doctorVoiceRoleMatcher
        ),
        SaarasTranscriptionEngine(
            PreferDeviceKeyTranscriptionGateway(
                hasDeviceKey = { deviceApiKeyProvider.get(ApiProvider.Sarvam) != null },
                direct = DirectSarvamTranscriptionGateway(
                    apiKey = { deviceApiKeyProvider.get(ApiProvider.Sarvam) }
                ),
                fallback = HttpRemoteTranscriptionGateway(
                    backendBaseUrl = BuildConfig.TRANSCRIPTION_BACKEND_URL,
                    allowInsecureLocalhost = BuildConfig.DEBUG
                )
            )
        ),
        AssemblyAiTranscriptionEngine(
            DirectAssemblyAiTranscriptionGateway(apiKey = {
                deviceApiKeyProvider.get(ApiProvider.AssemblyAI)
            })
        ),
        SherpaWhisperTranscriptionEngine(
            context.applicationContext,
            diarizationEngine,
            doctorVoiceRoleMatcher
        ),
        SherpaWhisperTranscriptionEngine(
            context.applicationContext,
            diarizationEngine,
            doctorVoiceRoleMatcher,
            WhisperModelVariant.Turbo
        ),
        SherpaWhisperTranscriptionEngine(
            context = context.applicationContext,
            diarizationEngine = null,
            doctorVoiceRoleMatcher = null,
            variant = WhisperModelVariant.Turbo,
            engineOption = TranscriptionEngineOption.WhisperTurboFullAudioTest,
            maxChunkSeconds = 30
        )
    ).associateBy(AudioTranscriptionEngine::option)

    override fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine {
        return checkNotNull(engines[option]) {
            "Transcription engine ${option.displayName} is unavailable."
        }
    }
}
