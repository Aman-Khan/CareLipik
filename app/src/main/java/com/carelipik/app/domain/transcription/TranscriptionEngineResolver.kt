package com.carelipik.app.domain.transcription

fun interface TranscriptionEngineResolver {
    fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine

    fun resolve(option: TranscriptionEngineOption, speakerCount: Int): AudioTranscriptionEngine {
        SpeakerCount.validate(speakerCount)
        return resolve(option)
    }
}
