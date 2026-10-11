package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.MedicalAssertion
import com.carelipik.app.domain.transcription.MedicalEntity
import com.carelipik.app.domain.transcription.MedicalEntityAnalysis
import com.carelipik.app.domain.transcription.MedicalEntityType
import com.carelipik.app.domain.transcription.MedicationMention

/**
 * Adds deterministic medication attributes to NER candidates. Rules never change transcript text
 * and never approve a medicine; every returned span still requires doctor confirmation.
 */
class MedicationAttributeRuleExtractor {
    fun analyze(text: String, nerEntities: List<MedicalEntity>): MedicalEntityAnalysis {
        val ruleEntities = buildList {
            addMatches(text, STRENGTH, MedicalEntityType.Strength)
            addMatches(text, DOSE, MedicalEntityType.Dose)
            addMatches(text, FREQUENCY, MedicalEntityType.Frequency)
            addMatches(text, DURATION, MedicalEntityType.Duration)
            addMatches(text, ROUTE, MedicalEntityType.Route)
        }
        val entities = (nerEntities + ruleEntities)
            .distinctBy { Triple(it.startIndex, it.endIndexExclusive, it.type) }
            .sortedBy { it.startIndex }
            .map { it.withAssertionFrom(text) }
        val attributes = entities.filter { it.type in ATTRIBUTE_TYPES }
        val medications = entities.filter { it.type == MedicalEntityType.Medication }.map { medicine ->
            val sentence = sentenceBounds(text, medicine.startIndex)
            val nearby = attributes.filter {
                it.startIndex in sentence.first until sentence.second &&
                    kotlin.math.abs(it.startIndex - medicine.endIndexExclusive) <= MAX_LINK_DISTANCE
            }
            MedicationMention(
                medication = medicine,
                strength = nearby.closestAfterOrBefore(medicine, MedicalEntityType.Strength),
                dose = nearby.closestAfterOrBefore(medicine, MedicalEntityType.Dose),
                frequency = nearby.closestAfterOrBefore(medicine, MedicalEntityType.Frequency),
                duration = nearby.closestAfterOrBefore(medicine, MedicalEntityType.Duration),
                route = nearby.closestAfterOrBefore(medicine, MedicalEntityType.Route)
            )
        }
        return MedicalEntityAnalysis(entities = entities, medications = medications)
    }

    private fun MutableList<MedicalEntity>.addMatches(
        text: String,
        pattern: Regex,
        type: MedicalEntityType
    ) {
        pattern.findAll(text).forEach { match ->
            add(
                MedicalEntity(
                    text = match.value,
                    startIndex = match.range.first,
                    endIndexExclusive = match.range.last + 1,
                    type = type,
                    confidence = 1f,
                    source = RULE_SOURCE
                )
            )
        }
    }

    private fun MedicalEntity.withAssertionFrom(text: String): MedicalEntity {
        if (type in ATTRIBUTE_TYPES) return this
        val prefix = text.substring(maxOf(0, startIndex - ASSERTION_WINDOW), startIndex).lowercase()
        val assertion = when {
            NEGATION.containsMatchIn(prefix) -> MedicalAssertion.Negated
            HISTORY.containsMatchIn(prefix) -> MedicalAssertion.Historical
            UNCERTAINTY.containsMatchIn(prefix) -> MedicalAssertion.Possible
            else -> MedicalAssertion.Present
        }
        return copy(assertion = assertion)
    }

    private fun List<MedicalEntity>.closestAfterOrBefore(
        medication: MedicalEntity,
        type: MedicalEntityType
    ): MedicalEntity? = filter { it.type == type }.minByOrNull { candidate ->
        val afterPenalty = if (candidate.startIndex >= medication.endIndexExclusive) 0 else 1_000
        afterPenalty + kotlin.math.abs(candidate.startIndex - medication.endIndexExclusive)
    }

    private fun sentenceBounds(text: String, index: Int): Pair<Int, Int> {
        val start = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), startIndex = index - 1)
            .let { if (it < 0) 0 else it + 1 }
        val end = text.indexOfAny(charArrayOf('.', '!', '?', '\n'), startIndex = index)
            .let { if (it < 0) text.length else it }
        return start to end
    }

    private companion object {
        const val RULE_SOURCE = "deterministic-rule"
        const val MAX_LINK_DISTANCE = 96
        const val ASSERTION_WINDOW = 48
        val ATTRIBUTE_TYPES = setOf(
            MedicalEntityType.Strength,
            MedicalEntityType.Dose,
            MedicalEntityType.Frequency,
            MedicalEntityType.Duration,
            MedicalEntityType.Route
        )
        val STRENGTH = Regex("(?:\\b\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|g|ml|%)\\b|\\d+(?:\\.\\d+)?\\s*(?:मिलीग्राम|एमजी))", RegexOption.IGNORE_CASE)
        val DOSE = Regex("\\b(?:half|one|two|1|2)\\s+(?:tablet|tablets|capsule|capsules|पैबलेट|गोली|गोलियां)\\b", RegexOption.IGNORE_CASE)
        val FREQUENCY = Regex("\\b(?:once|twice|three times)\\s+(?:daily|a day)|\\b(?:OD|BD|BID|TDS|TID|QID|HS|SOS)\\b|दिन में (?:एक|दो|तीन) बार", RegexOption.IGNORE_CASE)
        val DURATION = Regex("\\b(?:for\\s+)?\\d+\\s*(?:days?|weeks?|months?)\\b|\\d+\\s*(?:दिन|हफ्ते|महीने)(?:\\s+के लिए)?", RegexOption.IGNORE_CASE)
        val ROUTE = Regex("\\b(?:oral(?:ly)?|by mouth|intravenous|IV|intramuscular|IM|subcutaneous|SC|topical(?:ly)?|inhaled|नाक से|मुंह से)\\b", RegexOption.IGNORE_CASE)
        val NEGATION = Regex("(?:\\bno\\b|\\bnot\\b|\\bnever\\b|denies|नहीं|कभी नहीं)", RegexOption.IGNORE_CASE)
        val HISTORY = Regex("(?:history of|previously|used to|बचपन में|पहले)", RegexOption.IGNORE_CASE)
        val UNCERTAINTY = Regex("(?:possible|possibly|suspected|शायद|संभावित)", RegexOption.IGNORE_CASE)
    }
}
