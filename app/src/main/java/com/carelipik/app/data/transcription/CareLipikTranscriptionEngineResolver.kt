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
        )
    ).associateBy(AudioTranscriptionEngine::option)

    override fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine {
        return checkNotNull(engines[option]) {
            "Transcription engine ${option.displayName} is unavailable."
        }
    }
}
