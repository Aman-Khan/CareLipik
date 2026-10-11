package com.carelipik.app.domain.prescription

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.extraction.ClinicalNoteContentFormatter

data class PrescriptionAdviceSuggestion(
    val title: String,
    val content: String,
    val evidence: String
)

/** Projects generated plan/aftercare sections as unaccepted suggestions, never treatment decisions. */
object ReportPrescriptionSuggestions {
    fun advice(draft: ClinicalDraft): List<PrescriptionAdviceSuggestion> {
        if (draft.prescriptionContext.isNotEmpty()) return project(draft, setOf("advice", "precautions", "investigations", "follow_up"), true)
        return project(draft, setOf("plan", "aftercare")).flatMap { suggestion ->
            suggestion.content.split(Regex("(?<=[.!?])\\s+|[\\n;]+")).map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { content ->
                    medicineRegimen.containsMatchIn(content) ||
                        (draft.medications + draft.discussedMedications).any { medicine ->
                            medicine.name.isNotBlank() && content.contains(medicine.name, ignoreCase = true)
                        }
                }.map { suggestion.copy(title = "Advice", content = it) }
        }
    }

    fun symptoms(draft: ClinicalDraft): List<PrescriptionAdviceSuggestion> =
        if (draft.prescriptionContext.isNotEmpty()) project(draft, setOf("symptoms"), true)
        else project(draft,
        setOf("subjective", "chief_complaint", "chief_concern", "history_present_illness", "specialty_history", "data", "interval_history"))

    private val medicineRegimen = Regex("\\b(?:tablet|capsule|syrup|prescribe|prescribed|\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|ml))\\b", RegexOption.IGNORE_CASE)

    private fun project(draft: ClinicalDraft, sectionIds: Set<String>, context: Boolean = false): List<PrescriptionAdviceSuggestion> {
        if (draft.generationSource == ClinicalNoteGenerationSource.OfflineTranscript) return emptyList()
        val explicitTurns = Regex("(?m)(?=^(?:Doctor|Patient|Other|Noise|Speaker\\s+[^:]+):)")
            .split(draft.reviewedTranscript.trim()).map(String::trim).filter(String::isNotBlank)
        val turns = if (explicitTurns.size > 1) explicitTurns else draft.reviewedTranscript
            .lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        return (if (context) draft.prescriptionContext else draft.structuredSections).filter { it.id in sectionIds && it.content.isNotBlank() }
            .mapNotNull { section ->
                val evidence = section.sourceTurnIds.distinct().mapNotNull { id ->
                    Regex("T([1-9][0-9]*)").matchEntire(id)?.groupValues?.get(1)?.toIntOrNull()
                        ?.let { turns.getOrNull(it - 1) }
                }
                if (evidence.isEmpty()) null else PrescriptionAdviceSuggestion(
                    section.title, ClinicalNoteContentFormatter.clean(section.content), evidence.joinToString("\n")
                )
            }
    }
}
