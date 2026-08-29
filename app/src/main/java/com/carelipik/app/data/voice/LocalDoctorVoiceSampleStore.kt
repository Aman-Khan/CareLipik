package com.carelipik.app.data.voice

import android.content.Context
import com.carelipik.app.data.transcription.PcmWaveAudio
import com.carelipik.app.domain.model.RecordedAudio
import com.carelipik.app.domain.voice.DoctorVoiceEnrollmentRules
import com.carelipik.app.domain.voice.DoctorVoiceSample
import com.carelipik.app.domain.voice.DoctorVoiceSampleSaveResult
import com.carelipik.app.domain.voice.DoctorVoiceSampleStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalDoctorVoiceSampleStore(context: Context) : DoctorVoiceSampleStore {
    private val directory = File(context.filesDir, DIRECTORY_NAME)
    private val sampleFile = File(directory, SAMPLE_FILE_NAME)

    override suspend fun load(): DoctorVoiceSample? = withContext(Dispatchers.IO) {
        sampleFile.takeIf(File::isFile)?.let(::toSample)
    }

    override suspend fun save(recordedAudio: RecordedAudio): DoctorVoiceSampleSaveResult =
        withContext(Dispatchers.IO) {
            DoctorVoiceEnrollmentRules.validationMessage(recordedAudio.durationMillis)?.let {
                return@withContext DoctorVoiceSampleSaveResult.Failure(it)
            }
            runCatching {
                val source = File(recordedAudio.localPath)
                require(source.isFile && source.length() > 44L) {
                    "The voice sample could not be read. Please record it again."
                }
                DoctorVoiceEnrollmentRules.audioQualityMessage(
                    PcmWaveAudio.readMono16Khz(source)
                )?.let { message -> error(message) }
                directory.mkdirs()
                val pendingFile = File(directory, "$SAMPLE_FILE_NAME.pending")
                source.copyTo(pendingFile, overwrite = true)
                if (!pendingFile.renameTo(sampleFile)) {
                    pendingFile.copyTo(sampleFile, overwrite = true)
                    pendingFile.delete()
                }
                sampleFile.setLastModified(System.currentTimeMillis())
                toSample(sampleFile, recordedAudio.durationMillis)
            }.fold(
                onSuccess = DoctorVoiceSampleSaveResult::Success,
                onFailure = { error ->
                    DoctorVoiceSampleSaveResult.Failure(
                        error.message ?: "The doctor voice sample could not be saved."
                    )
                }
            )
        }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        sampleFile.delete()
        File(directory, "$SAMPLE_FILE_NAME.pending").delete()
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
        Unit
    }

    private fun toSample(file: File, durationMillis: Long = durationFromFile(file)) =
        DoctorVoiceSample(
            localPath = file.absolutePath,
            durationMillis = durationMillis,
            updatedAtMillis = file.lastModified()
        )

    private fun durationFromFile(file: File): Long =
        ((file.length() - WAVE_HEADER_BYTES).coerceAtLeast(0L) * 1_000L) /
            BYTES_PER_SECOND

    private companion object {
        const val DIRECTORY_NAME = "doctor_voice"
        const val SAMPLE_FILE_NAME = "doctor-voice-sample.wav"
        const val WAVE_HEADER_BYTES = 44L
        const val BYTES_PER_SECOND = 16_000L * 2L
    }
}
