package com.carelipik.app.domain.model

data class ClinicalDraft(
    val patientAge: String = "",
    val presentingComplaint: String = "",
    val history: String = "",
    val keyFindings: String = "",
    val assessmentNotes: String = "",
    val planNotes: String = "",
    val reviewedTranscript: String = "",
    val noteFormat: ClinicalNoteFormat = ClinicalNoteFormat.Soap,
    val noteLanguage: ClinicalNoteLanguage = ClinicalNoteLanguage.Original,
    val specialtyName: String = "",
    val structuredSections: List<ClinicalNoteSection> = emptyList(),
    val medications: List<MedicationDraft> = emptyList(),
    val coverageWarnings: List<String> = emptyList(),
    val generationSource: ClinicalNoteGenerationSource =
        ClinicalNoteGenerationSource.OfflineTranscript
) {
    val hasContent: Boolean
        get() = listOf(
            presentingComplaint,
            history,
            keyFindings,
            assessmentNotes,
            planNotes
        ).any(String::isNotBlank) ||
            structuredSections.any { it.content.isNotBlank() } ||
            medications.any { it.hasContent }

    val effectiveSections: List<ClinicalNoteSection>
        get() = structuredSections.ifEmpty {
            listOf(
                ClinicalNoteSection("presenting_complaint", "Presenting complaint", presentingComplaint),
                ClinicalNoteSection("history", "History", history),
                ClinicalNoteSection("key_findings", "Key findings", keyFindings),
                ClinicalNoteSection("assessment", "Assessment notes", assessmentNotes),
                ClinicalNoteSection("plan", "Plan notes", planNotes)
            )
        }
}
