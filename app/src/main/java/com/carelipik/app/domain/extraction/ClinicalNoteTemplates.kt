package com.carelipik.app.domain.extraction

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.model.ClinicalNoteSection

/** Conservative offline template mapping. Empty clinical fields remain explicitly empty. */
object ClinicalNoteTemplates {
    fun apply(
        draft: ClinicalDraft,
        format: ClinicalNoteFormat,
        language: ClinicalNoteLanguage,
        specialtyName: String
    ): ClinicalDraft {
        val content = offlineContent(draft, format)
        return draft.copy(
            noteFormat = format,
            noteLanguage = language,
            specialtyName = specialtyName,
            structuredSections = format.sectionDefinitions.map { definition ->
                ClinicalNoteSection(
                    id = definition.id,
                    title = specialtyTitle(definition.title, format, specialtyName),
                    content = content[definition.id].orEmpty()
                )
            },
            coverageWarnings = emptyList(),
            generationSource = ClinicalNoteGenerationSource.OfflineTranscript
        )
    }

    private fun offlineContent(
        draft: ClinicalDraft,
        format: ClinicalNoteFormat
    ): Map<String, String> = when (format) {
        ClinicalNoteFormat.Soap,
        ClinicalNoteFormat.Apso -> mapOf(
            "subjective" to draft.keyFindings.ifBlank {
                listOf(draft.presentingComplaint, draft.history)
                    .filter(String::isNotBlank)
                    .joinToString("\n")
            },
            "assessment" to draft.assessmentNotes,
            "plan" to draft.planNotes
        )
        ClinicalNoteFormat.HistoryAndPhysical -> mapOf(
            "chief_complaint" to draft.presentingComplaint,
            "history_present_illness" to draft.history,
            "assessment" to draft.assessmentNotes,
            "plan" to draft.planNotes
        )
        ClinicalNoteFormat.ProblemOriented -> mapOf(
            "problem_list" to draft.presentingComplaint,
            "problem_findings" to draft.keyFindings,
            "assessment" to draft.assessmentNotes,
            "plan" to draft.planNotes
        )
        ClinicalNoteFormat.Progress -> mapOf(
            "interval_history" to draft.history,
            "current_findings" to draft.keyFindings,
            "assessment" to draft.assessmentNotes,
            "plan" to draft.planNotes
        )
        ClinicalNoteFormat.Dap -> mapOf(
            "data" to draft.keyFindings,
            "assessment" to draft.assessmentNotes,
            "plan" to draft.planNotes
        )
        ClinicalNoteFormat.Birp -> mapOf(
            "behaviour" to draft.keyFindings,
            "plan" to draft.planNotes
        )
        ClinicalNoteFormat.Girp -> mapOf(
            "goal" to draft.presentingComplaint,
            "response" to draft.keyFindings,
            "plan" to draft.planNotes
        )
        ClinicalNoteFormat.Procedure -> mapOf(
            "indication" to draft.presentingComplaint,
            "findings" to draft.keyFindings,
            "aftercare" to draft.planNotes
        )
        ClinicalNoteFormat.CustomSpecialty -> mapOf(
            "chief_concern" to draft.presentingComplaint,
            "specialty_history" to draft.history,
            "specialty_findings" to draft.keyFindings,
            "assessment" to draft.assessmentNotes,
            "plan" to draft.planNotes
        )
    }

    private fun specialtyTitle(
        title: String,
        format: ClinicalNoteFormat,
        specialtyName: String
    ): String = if (
        format == ClinicalNoteFormat.CustomSpecialty &&
        title.startsWith("Specialty") &&
        specialtyName.isNotBlank()
    ) {
        title.replace("Specialty", specialtyName.trim())
    } else {
        title
    }
}
