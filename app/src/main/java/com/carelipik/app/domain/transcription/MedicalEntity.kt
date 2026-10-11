package com.carelipik.app.domain.transcription

enum class MedicalEntityType {
    Medication,
    Disease,
    Symptom,
    Allergy,
    Investigation,
    Strength,
    Dose,
    Frequency,
    Duration,
    Route,
    Other
}

enum class MedicalAssertion {
    Present,
    Negated,
    Possible,
    Historical
}

data class MedicalEntity(
    val text: String,
    val startIndex: Int,
    val endIndexExclusive: Int,
    val type: MedicalEntityType,
    val confidence: Float,
    val assertion: MedicalAssertion = MedicalAssertion.Present,
    val source: String
) {
    init {
        require(startIndex >= 0)
        require(endIndexExclusive > startIndex)
        require(confidence in 0f..1f)
    }
}

data class MedicationMention(
    val medication: MedicalEntity,
    val strength: MedicalEntity? = null,
    val dose: MedicalEntity? = null,
    val frequency: MedicalEntity? = null,
    val duration: MedicalEntity? = null,
    val route: MedicalEntity? = null
)

data class MedicalEntityAnalysis(
    val entities: List<MedicalEntity>,
    val medications: List<MedicationMention>
)

fun interface MedicalNamedEntityRecognizer {
    fun recognize(text: String): List<MedicalEntity>
}
