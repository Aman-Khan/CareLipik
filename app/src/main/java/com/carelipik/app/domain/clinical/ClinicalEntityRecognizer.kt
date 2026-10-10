package com.carelipik.app.domain.clinical

enum class ClinicalEntityType {
    Medication,
    GenericSalt,
    Disease,
    Symptom,
    Allergy,
    Strength,
    Dosage,
    Frequency,
    Duration,
    Route,
    Investigation,
    Procedure,
    Anatomy
}

data class ClinicalEntityCandidate(
    val originalStart: Int,
    val originalEndExclusive: Int,
    val sourceText: String,
    val type: ClinicalEntityType,
    val confidence: Float
)

/** Boundary for the future fine-tuned token-classification model. */
fun interface ClinicalEntityRecognizer {
    fun recognize(text: NormalizedClinicalText): List<ClinicalEntityCandidate>
}
