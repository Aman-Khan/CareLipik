package com.carelipik.app.domain.export

import com.carelipik.app.domain.model.RecordedAudio

sealed interface RecordingAudioExportResult {
    data class Success(val sizeBytes: Long) : RecordingAudioExportResult
    data class Failure(val message: String) : RecordingAudioExportResult
}

/** Copies the original WAV to a new document selected by the user. */
fun interface RecordingAudioExporter {
    suspend fun export(audio: RecordedAudio, destinationUri: String): RecordingAudioExportResult
}
