package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.BuildConfig
import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver

class CareLipikTranscriptionEngineResolver(context: Context) : TranscriptionEngineResolver {
    private val diarizationEngine = SherpaOfflineSpeakerDiarizationEngine(
        context.applicationContext
    )
    private val engines: Map<TranscriptionEngineOption, AudioTranscriptionEngine> = listOf(
        SherpaMedAsrTranscriptionEngine(context.applicationContext, diarizationEngine),
        SaarasTranscriptionEngine(
            HttpRemoteTranscriptionGateway(
                backendBaseUrl = BuildConfig.TRANSCRIPTION_BACKEND_URL,
                allowInsecureLocalhost = BuildConfig.DEBUG
            )
        ),
        SherpaWhisperTranscriptionEngine(context.applicationContext, diarizationEngine)
    ).associateBy(AudioTranscriptionEngine::option)

    override fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine {
        return checkNotNull(engines[option]) {
            "Transcription engine ${option.displayName} is unavailable."
        }
    }
}
