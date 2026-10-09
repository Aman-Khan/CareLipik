package com.carelipik.app.domain.recording

import com.carelipik.app.domain.model.RecordedAudio
import kotlinx.coroutines.flow.StateFlow

sealed interface AudioImportResult {
    data class Success(val audio: RecordedAudio) : AudioImportResult
    data class Failure(val message: String) : AudioImportResult
}

/** Boundary for consultation audio capture, temporary import and playback. */
interface ConsultationRecorder {
    /** Normalized microphone energy from 0 (silence) to 1 (loud). */
    val amplitude: StateFlow<Float>
    val recordedAudio: StateFlow<RecordedAudio?>
    val isPlaying: StateFlow<Boolean>

    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun discard()
    fun play()
    fun stopPlayback()
    suspend fun importAudio(sourceUri: String): AudioImportResult
    /** Takes ownership of a decrypted app-private temporary recording. */
    suspend fun restoreAudio(audio: RecordedAudio): AudioImportResult =
        AudioImportResult.Failure("Saved recording playback is unavailable in this recorder.")
}
