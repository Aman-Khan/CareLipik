package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.BuildConfig
import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver
import com.carelipik.app.data.voice.SherpaDoctorVoiceRoleMatcher

class CareLipikTranscriptionEngineResolver(context: Context) : TranscriptionEngineResolver {
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
            HttpRemoteTranscriptionGateway(
                backendBaseUrl = BuildConfig.TRANSCRIPTION_BACKEND_URL,
                allowInsecureLocalhost = BuildConfig.DEBUG
            )
        ),
        SherpaWhisperTranscriptionEngine(
            context.applicationContext,
            diarizationEngine,
            doctorVoiceRoleMatcher
        )
    ).associateBy(AudioTranscriptionEngine::option)

    override fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine {
        return checkNotNull(engines[option]) {
            "Transcription engine ${option.displayName} is unavailable."
        }
    }
}
