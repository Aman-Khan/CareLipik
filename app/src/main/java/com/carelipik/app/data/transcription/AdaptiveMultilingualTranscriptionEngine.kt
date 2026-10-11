package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult

/** Uses the hosted multilingual engine when reachable and the local Turbo model otherwise. */
class AdaptiveMultilingualTranscriptionEngine(
    private val isOnline: () -> Boolean,
    private val onlineEngine: AudioTranscriptionEngine,
    private val offlineEngine: AudioTranscriptionEngine
) : AudioTranscriptionEngine {
    override val option: TranscriptionEngineOption = TranscriptionEngineOption.AssemblyAiUniversal

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage
    ): TranscriptionResult = if (isOnline()) {
        when (val result = onlineEngine.transcribe(audioPath, language)) {
            is TranscriptionResult.Success -> result
            is TranscriptionResult.Failure -> offlineEngine.transcribe(audioPath, language)
        }
    } else {
        offlineEngine.transcribe(audioPath, language)
    }
}
