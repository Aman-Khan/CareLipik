package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.BuildConfig
import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver
import com.carelipik.app.data.voice.SherpaDoctorVoiceRoleMatcher
import com.carelipik.app.data.local.DeviceApiKeyProvider
import com.carelipik.app.domain.repository.ApiProvider
import com.carelipik.app.domain.transcription.SpeakerCount
import com.carelipik.app.domain.transcription.FixedCountSpeakerDiarizationEngine

class CareLipikTranscriptionEngineResolver(
    private val context: Context,
    private val speakerCount: Int? = null
) : TranscriptionEngineResolver {
    private val deviceApiKeyProvider = DeviceApiKeyProvider(context.applicationContext)
    private val localDiarizationEngine = SherpaOfflineSpeakerDiarizationEngine(
        context.applicationContext
    )
    private val diarizationEngine = speakerCount?.let {
        FixedCountSpeakerDiarizationEngine(localDiarizationEngine, it)
    } ?: localDiarizationEngine
    private val doctorVoiceRoleMatcher = SherpaDoctorVoiceRoleMatcher(context.applicationContext)
    private val whisper = SherpaWhisperTranscriptionEngine(
        context.applicationContext, diarizationEngine, doctorVoiceRoleMatcher
    )
    private val medAsr = SherpaMedAsrTranscriptionEngine(
        context.applicationContext, diarizationEngine, doctorVoiceRoleMatcher
    )
    private val engines: Map<TranscriptionEngineOption, AudioTranscriptionEngine> = listOf(
        medAsr,
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
            ),
            expectedSpeakerCount = speakerCount
        ),
        whisper,
        HybridWhisperMedAsrTranscriptionEngine(whisper, medAsr)
    ).associateBy(AudioTranscriptionEngine::option)

    override fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine {
        return checkNotNull(engines[option]) {
            "Transcription engine ${option.displayName} is unavailable."
        }
    }

    override fun resolve(option: TranscriptionEngineOption, speakerCount: Int): AudioTranscriptionEngine =
        CareLipikTranscriptionEngineResolver(context, SpeakerCount.validate(speakerCount)).resolve(option)
}
