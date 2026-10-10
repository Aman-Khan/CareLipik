package com.carelipik.app.domain.prescription

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.MedicationDraft

data class PrescriptionMedicine(
    val id: String,
    val medicine: MedicationDraft = MedicationDraft(),
    val quantity: String = ""
)

data class Prescription(
    val patientName: String = "",
    val patientAge: String = "",
    val doctorName: String = "",
    val registrationNumber: String = "",
    val clinicName: String = "",
    val date: String = "",
    val medicines: List<PrescriptionMedicine> = emptyList(),
    val advice: String = "",
    val followUp: String = ""
)

data class PrescriptionFiles(val pdfPath: String, val latexPath: String, val baseName: String)

interface PrescriptionExporter {
    suspend fun generate(prescription: Prescription, onProgress: (String) -> Unit = {}): PrescriptionFiles
    suspend fun save(localPath: String, destination: String)
}

/** Mentions are selection candidates, never treatment recommendations or inferred dosages. */
object DiscussedMedicineSuggestions {
    fun from(draft: ClinicalDraft): List<MedicationDraft> {
        val result = draft.medications.filter { it.name.isNotBlank() }.toMutableList()
        val transcript = draft.reviewedTranscript
        val names = listOf("paracetamol", "acetaminophen", "ibuprofen", "metformin", "amoxicillin",
            "azithromycin", "cetirizine", "levocetirizine", "omeprazole", "pantoprazole",
            "atorvastatin", "rosuvastatin", "amlodipine", "telmisartan", "losartan", "aspirin",
            "insulin", "salbutamol", "montelukast", "dolo", "crocin", "calpol", "augmentin")
        names.forEach { name ->
            Regex("\\b${Regex.escape(name)}\\b", RegexOption.IGNORE_CASE).find(transcript)?.let { match ->
                val start = (match.range.first - 100).coerceAtLeast(0)
                val end = (match.range.last + 101).coerceAtMost(transcript.length)
                result += MedicationDraft(name = match.value, sourceEvidence = transcript.substring(start, end))
            }
        }
        // Explicit dosage-form cues also allow unfamiliar brands without a drug catalogue.
        Regex("\\b(?:tablet|capsule|syrup|injection|tab\\.|cap\\.)\\s+([\\p{L}][\\p{L}\\p{N}-]{2,40})",
            RegexOption.IGNORE_CASE).findAll(transcript).forEach { match ->
            val name = match.groupValues[1]
            if (name.lowercase() !in setOf("of", "the", "for", "once", "twice", "daily", "after", "before", "and", "with", "that", "this")) {
                result += MedicationDraft(name = name, sourceEvidence = match.value)
            }
        }
        return result.distinctBy { it.name.trim().lowercase() }.take(30)
    }
}
