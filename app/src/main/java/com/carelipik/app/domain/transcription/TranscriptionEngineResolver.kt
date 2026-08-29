package com.carelipik.app.domain.transcription

fun interface TranscriptionEngineResolver {
    fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine
}
