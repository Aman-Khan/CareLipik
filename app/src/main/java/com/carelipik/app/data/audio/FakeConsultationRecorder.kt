package com.carelipik.app.data.audio

import com.carelipik.app.domain.recording.ConsultationRecorder
import com.carelipik.app.domain.recording.AudioImportResult
import com.carelipik.app.domain.model.RecordedAudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** UI-development recorder that intentionally creates no audio or patient data. */
class FakeConsultationRecorder : ConsultationRecorder {
    override val amplitude: StateFlow<Float> = MutableStateFlow(0f)
    override val recordedAudio: StateFlow<RecordedAudio?> = MutableStateFlow(null)
    override val isPlaying: StateFlow<Boolean> = MutableStateFlow(false)

    override fun start() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun stop() = Unit
    override fun discard() = Unit
    override fun play() = Unit
    override fun stopPlayback() = Unit
    override suspend fun importAudio(sourceUri: String): AudioImportResult =
        AudioImportResult.Failure("Audio import is unavailable in the preview recorder.")
}
