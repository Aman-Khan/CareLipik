package com.carelipik.app.domain.voice

import com.carelipik.app.domain.model.RecordedAudio

data class DoctorVoiceSample(
    val localPath: String,
    val durationMillis: Long,
    val updatedAtMillis: Long,
    val enrolledSampleCount: Int = 1
)

sealed interface DoctorVoiceSampleSaveResult {
    data class Success(val sample: DoctorVoiceSample) : DoctorVoiceSampleSaveResult
    data class Failure(val message: String) : DoctorVoiceSampleSaveResult
}

/** Stores the doctor's enrollment sample in app-private on-device storage. */
interface DoctorVoiceSampleStore {
    suspend fun load(): DoctorVoiceSample?
    suspend fun save(recordedAudio: RecordedAudio): DoctorVoiceSampleSaveResult
    suspend fun delete()
}
