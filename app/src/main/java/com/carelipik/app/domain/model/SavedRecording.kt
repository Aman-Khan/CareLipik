package com.carelipik.app.domain.model

import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage

/** An unfinished consultation, separate from doctor-approved documentation. */
data class SavedRecording(
    val id: String,
    val savedAtMillis: Long,
    val patientName: String,
    val patientAge: String,
    val visitReason: String,
    val language: TranscriptionLanguage,
    val engine: TranscriptionEngineOption,
    val audioDisplayName: String,
    val durationMillis: Long,
    val audioSource: RecordedAudioSource,
    val hasRecordingConsent: Boolean
)

data class RestoredSavedRecording(val recording: SavedRecording, val audio: RecordedAudio)
