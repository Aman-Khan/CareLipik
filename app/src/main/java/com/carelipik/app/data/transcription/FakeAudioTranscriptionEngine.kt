package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption

/** Deterministic prototype output; it does not inspect or upload the recording. */
class FakeAudioTranscriptionEngine : AudioTranscriptionEngine {
    override val option: TranscriptionEngineOption = TranscriptionEngineOption.WhisperMultilingual

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage
    ): TranscriptionResult {
        if (audioPath.isBlank()) return TranscriptionResult.Failure("The recording is unavailable.")
        return TranscriptionResult.Success(
            "Speaker 1: What brings you in today?\n\n" +
                "Speaker 2: I have had a mild cough for three days.\n\n" +
                "Speaker 1: Have you noticed any fever or difficulty breathing?"
        )
    }
}
