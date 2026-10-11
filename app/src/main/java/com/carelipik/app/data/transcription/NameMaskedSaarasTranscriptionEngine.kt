package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.PatientNameAudioMasker
import com.carelipik.app.domain.transcription.PatientNameMaskingResult
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.PatientNameTranscriptRedactor
import com.carelipik.app.domain.transcription.ProgressAwareTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionStage
import java.io.File

/** Only the scrubbed copy crosses the remote engine boundary; cleanup happens after every upload. */
class NameMaskedSaarasTranscriptionEngine(
    private val delegate: AudioTranscriptionEngine,
    private val masker: PatientNameAudioMasker,
    private val patientName: String
) : ProgressAwareTranscriptionEngine {
    override val option = TranscriptionEngineOption.NameMaskedSaaras

    override fun transcribe(audioPath: String, language: TranscriptionLanguage): TranscriptionResult =
        transcribe(audioPath, language, {}, {})

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage,
        onProgress: (TranscriptionStage) -> Unit,
        checkCancelled: () -> Unit
    ): TranscriptionResult {
        if (language != TranscriptionLanguage.English) return TranscriptionResult.Failure("Patient-name masking supports English only. No audio was uploaded.")
        if (patientName.isBlank()) return TranscriptionResult.Failure("Enter the patient name before using masked Sarvam. No audio was uploaded.")
        checkCancelled()
        onProgress(TranscriptionStage.PreprocessingAudio)
        val masked = try {
            masker.mask(audioPath, patientName, language)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return TranscriptionResult.Failure("Patient-name masking failed. No audio was uploaded.")
        }
        if (masked is PatientNameMaskingResult.Failure) return TranscriptionResult.Failure(masked.message)
        masked as PatientNameMaskingResult.Success
        val copy = File(masked.audioPath)
        if (copy.canonicalPath == File(audioPath).canonicalPath) {
            return TranscriptionResult.Failure("A separate masked recording could not be verified. No audio was uploaded.")
        }
        return try {
            if (masked.windows.isEmpty() || !copy.isFile) {
                return TranscriptionResult.Failure("A separate masked recording could not be verified. No audio was uploaded.")
            }
            checkCancelled()
            onProgress(TranscriptionStage.SarvamTranscription)
            val result = delegate.transcribe(copy.absolutePath, language)
            checkCancelled()
            if (result is TranscriptionResult.Success) onProgress(TranscriptionStage.RedactingPatientName)
            when (result) {
                is TranscriptionResult.Success -> result.copy(
                    transcript = PatientNameTranscriptRedactor.redact(result.transcript, patientName),
                    segments = result.segments.map { segment ->
                        segment.copy(transcript = PatientNameTranscriptRedactor.redact(segment.transcript, patientName))
                    },
                    speakerSeparationWarning = listOfNotNull(
                        "Patient-name masking replaced ${masked.windows.size} estimated audio window(s) with noise. Nearby speech may also be absent; verify the transcript against the local recording.",
                        result.speakerSeparationWarning
                    ).joinToString(" ")
                )
                is TranscriptionResult.Failure -> result
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            TranscriptionResult.Failure("Masked Sarvam transcription failed. Retry to create a new masked copy.")
        } finally {
            copy.delete()
        }
    }
}
