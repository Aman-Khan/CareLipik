package com.carelipik.app.domain.recording

import com.carelipik.app.domain.model.RecordedAudio
import kotlinx.coroutines.flow.StateFlow

/** Boundary for consultation audio capture. Real device recording will implement this later. */
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
}
