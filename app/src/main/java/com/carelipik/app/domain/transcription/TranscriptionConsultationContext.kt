package com.carelipik.app.domain.transcription

/** Entered consultation details are context, never a source of missing spoken words. */
data class TranscriptionConsultationContext(
    val patientName: String = "",
    val patientAge: String = "",
    val visitReason: String = "",
    val expectedSpeakerCount: Int? = null,
    val speakerRoles: Map<String, String> = emptyMap()
)

interface ConsultationAwareTranscriptionEngine : ProgressAwareTranscriptionEngine {
    fun transcribeWithContext(audioPath: String, language: TranscriptionLanguage,
        context: TranscriptionConsultationContext, onProgress: (TranscriptionStage) -> Unit,
        checkCancelled: () -> Unit, onDetail: (String) -> Unit): TranscriptionResult
}
