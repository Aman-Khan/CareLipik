package com.carelipik.app.domain.model

enum class RecordedAudioSource {
    Microphone,
    Imported
}

data class RecordedAudio(
    val localPath: String,
    val sizeBytes: Long,
    val displayName: String = "Consultation recording",
    val durationMillis: Long = 0,
    val source: RecordedAudioSource = RecordedAudioSource.Microphone
)
