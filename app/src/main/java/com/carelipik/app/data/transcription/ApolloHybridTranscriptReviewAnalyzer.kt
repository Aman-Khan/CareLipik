package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.MedicalEntity
import com.carelipik.app.domain.transcription.MedicalEntityType
import com.carelipik.app.domain.transcription.MedicalNamedEntityRecognizer
import com.carelipik.app.domain.transcription.TranscriptConcern
import com.carelipik.app.domain.transcription.TranscriptConcernType
import com.carelipik.app.domain.transcription.TranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.TranscriptionLanguage

/** Combines Apollo Medical NER candidates with deterministic medication-attribute rules. */
class ApolloHybridTranscriptReviewAnalyzer(
    private val recognizer: MedicalNamedEntityRecognizer,
    private val ruleExtractor: MedicationAttributeRuleExtractor = MedicationAttributeRuleExtractor(),
    private val fallback: TranscriptReviewAnalyzer = RuleBasedTranscriptReviewAnalyzer(),
    // Scores over 83 token labels are not calibrated clinical certainty. Highlight candidates
    // for review at a recall-oriented threshold; every span remains unconfirmed.
    private val minimumModelConfidence: Float = 0.15f
) : TranscriptReviewAnalyzer {
    override fun analyze(
        transcript: String,
        language: TranscriptionLanguage
    ): List<TranscriptConcern> {
        if (transcript.isBlank()) return emptyList()
        val modelEntities = runCatching { recognizer.recognize(transcript) }
            .getOrElse { emptyList() }
            .filter { it.confidence >= minimumModelConfidence }
            .filter { it.startIndex >= 0 && it.endIndexExclusive <= transcript.length }
            .filter { transcript.substring(it.startIndex, it.endIndexExclusive) == it.text }
        val analysis = ruleExtractor.analyze(transcript, modelEntities)
        val modelConcerns = analysis.entities.map { it.toConcern() }
        val fallbackConcerns = fallback.analyze(transcript, language)
        return (modelConcerns + fallbackConcerns)
            .sortedWith(compareBy<TranscriptConcern> { it.startIndex }.thenByDescending {
                it.endIndexExclusive - it.startIndex
            })
            .fold(mutableListOf()) { accepted, candidate ->
                if (accepted.none { existing -> existing.overlaps(candidate) }) accepted += candidate
                accepted
            }
    }

    private fun MedicalEntity.toConcern(): TranscriptConcern = TranscriptConcern(
        id = "${source}:${type.name}:$startIndex:${text.lowercase()}",
        text = text,
        startIndex = startIndex,
        endIndexExclusive = endIndexExclusive,
        type = TranscriptConcernType.MedicalTerm,
        reason = buildString {
            append(type.displayName)
            append(if (source == "deterministic-rule") " identified by deterministic rules" else " identified by offline medical NER")
            if (source != "deterministic-rule") {
                append(" (model score: ")
                append((confidence * 100).toInt())
                append("%; unverified candidate)")
            }
            append(". Confirm against the recording; assertion: ")
            append(assertion.name.lowercase())
            append('.')
        }
    )

    private fun TranscriptConcern.overlaps(other: TranscriptConcern): Boolean =
        startIndex < other.endIndexExclusive && other.startIndex < endIndexExclusive

    private val MedicalEntityType.displayName: String
        get() = name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()

    private companion object {
        val ATTRIBUTE_TYPES = setOf(
            MedicalEntityType.Strength,
            MedicalEntityType.Dose,
            MedicalEntityType.Frequency,
            MedicalEntityType.Duration,
            MedicalEntityType.Route
        )
    }
}
