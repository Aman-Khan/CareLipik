package com.carelipik.app.domain.extraction

import com.carelipik.app.domain.model.MedicationDraft

/** Extracts only explicit doctor prescriptions that include a medicine and numeric strength. */
object ConservativePrescriptionExtractor {
    fun extract(transcript: String): List<MedicationDraft> = DOCTOR_TURN
        .findAll(transcript)
        .flatMap { turn -> PRESCRIPTION.findAll(turn.groupValues[1]) }
        .map { match ->
            val evidence = match.value.trim().trimEnd('.', ',', ';')
            MedicationDraft(
                name = match.groupValues[1].trim(),
                strength = match.groupValues[2].replace(Regex("\\s+"), " ").trim(),
                dose = DOSE.find(evidence)?.value.orEmpty(),
                frequency = FREQUENCY.find(evidence)?.value.orEmpty(),
                duration = DURATION.find(evidence)?.value.orEmpty(),
                route = ROUTE.find(evidence)?.value.orEmpty(),
                sourceEvidence = evidence,
                isDoctorReviewed = false
            )
        }
        .distinctBy { "${it.name.lowercase()}|${it.strength.lowercase()}" }
        .take(MAX_MEDICATIONS)
        .toList()

    private const val MAX_MEDICATIONS = 20
    private val DOCTOR_TURN = Regex(
        "(?ims)^Doctor\\s*:\\s*(.+?)(?=^(?:Doctor|Patient|Other|Noise|Speaker\\s+[^:]+)\\s*:|\\z)"
    )
    private val PRESCRIPTION = Regex(
        "(?i)\\b(?:prescrib(?:e|ing)|start|take|recommend|continue|give)\\s+(?:you\\s+)?" +
            "([a-z][a-z-]*(?:\\s+[a-z][a-z-]*){0,2})\\s+" +
            "(\\d+(?:\\.\\d+)?\\s*(?:mcg|mg|g|ml|units?|iu))\\b[^.!?\\n]*"
    )
    private val DOSE = Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:tablets?|capsules?|puffs?|drops?|ml)\\b")
    private val FREQUENCY = Regex(
        "(?i)\\b(?:once|twice|three times|four times)\\s+(?:a\\s+day|daily)|" +
            "\\b(?:daily|nightly|morning|bedtime|every\\s+\\d+\\s+hours?)\\b"
    )
    private val DURATION = Regex("(?i)\\bfor\\s+\\d+\\s+(?:days?|weeks?|months?)\\b")
    private val ROUTE = Regex("(?i)\\b(?:oral(?:ly)?|topical(?:ly)?|inhaled|intravenous|iv|im)\\b")
}
