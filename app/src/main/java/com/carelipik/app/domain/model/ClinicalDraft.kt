package com.carelipik.app.domain.model

data class ClinicalDraft(
    val patientAge: String = "",
    val presentingComplaint: String = "",
    val history: String = "",
    val keyFindings: String = "",
    val assessmentNotes: String = "",
    val planNotes: String = "",
    val reviewedTranscript: String = ""
) {
    val hasContent: Boolean
        get() = listOf(
            presentingComplaint,
            history,
            keyFindings,
            assessmentNotes,
            planNotes,
            reviewedTranscript
        ).any(String::isNotBlank)
}
