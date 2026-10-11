package com.carelipik.app.data.transcription

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
    private val connectivityManager = context.applicationContext.getSystemService(
        ConnectivityManager::class.java
    )
    private val localTurboEngine = SherpaWhisperTranscriptionEngine(
        context.applicationContext,
        diarizationEngine,
        doctorVoiceRoleMatcher,
        WhisperModelVariant.Turbo
    )
    private val hostedMultilingualEngine = AssemblyAiTranscriptionEngine(
        DirectAssemblyAiTranscriptionGateway(apiKey = {
            deviceApiKeyProvider.get(ApiProvider.AssemblyAI)
        })
    )
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
        AdaptiveMultilingualTranscriptionEngine(
            isOnline = {
                connectivityManager.activeNetwork?.let { network ->
                    connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    }
                } ?: false
            },
            onlineEngine = hostedMultilingualEngine,
            offlineEngine = localTurboEngine
        ),
        SherpaWhisperTranscriptionEngine(
            context.applicationContext,
            diarizationEngine,
            doctorVoiceRoleMatcher
        ),
        localTurboEngine,
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
