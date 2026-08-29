package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver

class LocalTranscriptionEngineResolver(context: Context) : TranscriptionEngineResolver {
    private val engines: Map<TranscriptionEngineOption, AudioTranscriptionEngine> = listOf(
        SherpaMedAsrTranscriptionEngine(context.applicationContext),
        SherpaWhisperTranscriptionEngine(context.applicationContext)
    ).associateBy(AudioTranscriptionEngine::option)

    override fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine {
        return checkNotNull(engines[option]) {
            "Transcription engine ${option.displayName} is unavailable."
        }
    }
}
