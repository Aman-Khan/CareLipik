package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.TranscriptConcern
import com.carelipik.app.domain.transcription.TranscriptConcernType
import com.carelipik.app.domain.transcription.TranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.TranscriptionLanguage

/**
 * Conservative offline review aid for common clinical words and known ASR confusions.
 * It does not claim to provide model confidence or medical interpretation.
 */
class RuleBasedTranscriptReviewAnalyzer : TranscriptReviewAnalyzer {
    override fun analyze(
        transcript: String,
        language: TranscriptionLanguage
    ): List<TranscriptConcern> {
        if (transcript.isBlank()) return emptyList()
        val candidates = rules
            .filter { it.supports(language) }
            .flatMap { rule ->
                rule.pattern.findAll(transcript).map { match ->
                    TranscriptConcern(
                        id = concernId(rule, match.range.first, match.value),
                        text = match.value,
                        startIndex = match.range.first,
                        endIndexExclusive = match.range.last + 1,
                        type = rule.type,
                        reason = rule.reason,
                        suggestedReplacement = rule.suggestedReplacement
                    )
                }
            }
            .sortedWith(
                compareBy<TranscriptConcern> { it.startIndex }
                    .thenByDescending { it.endIndexExclusive - it.startIndex }
                    .thenBy { it.type.ordinal }
            )

        return candidates.fold(mutableListOf()) { accepted, candidate ->
            if (accepted.none { it.overlaps(candidate) }) accepted += candidate
            accepted
        }
    }

    private fun ReviewRule.supports(language: TranscriptionLanguage): Boolean =
        language == TranscriptionLanguage.Auto ||
            language in languages ||
            language == TranscriptionLanguage.Hinglish

    private fun TranscriptConcern.overlaps(other: TranscriptConcern): Boolean =
        startIndex < other.endIndexExclusive && other.startIndex < endIndexExclusive

    private fun concernId(rule: ReviewRule, startIndex: Int, matchedText: String): String =
        "${rule.type.name}:$startIndex:${matchedText.lowercase()}"

    private data class ReviewRule(
        val term: String,
        val type: TranscriptConcernType,
        val languages: Set<TranscriptionLanguage>,
        val suggestedReplacement: String? = null,
        val customPattern: Regex? = null
    ) {
        val pattern = customPattern ?: Regex(
            pattern = "(?<![\\p{L}\\p{M}\\p{N}])${Regex.escape(term)}" +
                "(?![\\p{L}\\p{M}\\p{N}])",
            option = RegexOption.IGNORE_CASE
        )
        val reason: String = when (type) {
            TranscriptConcernType.PossibleRecognitionError ->
                "This wording is commonly confused by speech recognition. Check the audio."
            TranscriptConcernType.MedicalTerm ->
                "Clinical wording can change the meaning of the note. Confirm it was heard correctly."
        }
    }

    private companion object {
        val ENGLISH = setOf(TranscriptionLanguage.English, TranscriptionLanguage.Hinglish)
        val HINDI = setOf(TranscriptionLanguage.Hindi, TranscriptionLanguage.Hinglish)
        val ALL = ENGLISH + HINDI

        val rules = listOf(
            ReviewRule("para seat amol", TranscriptConcernType.PossibleRecognitionError, ALL, "paracetamol"),
            ReviewRule("paracitamol", TranscriptConcernType.PossibleRecognitionError, ALL, "paracetamol"),
            ReviewRule("cup", TranscriptConcernType.PossibleRecognitionError, ALL, "cough"),
            ReviewRule("coff", TranscriptConcernType.PossibleRecognitionError, ALL, "cough"),
            ReviewRule("cuff", TranscriptConcernType.PossibleRecognitionError, ALL, "cough"),
            ReviewRule("golf", TranscriptConcernType.PossibleRecognitionError, ALL, "cough"),
            ReviewRule(
                "Iron",
                TranscriptConcernType.PossibleRecognitionError,
                ALL,
                "I am",
                Regex("\\bIron(?=\\s+\\d{1,3}\\s+(?:years?|yrs?)\\s+old\\b)", RegexOption.IGNORE_CASE)
            ),
            ReviewRule("soar throat", TranscriptConcernType.PossibleRecognitionError, ALL, "sore throat"),
            ReviewRule("yellow flame", TranscriptConcernType.PossibleRecognitionError, ALL, "yellow phlegm"),
            ReviewRule("loose tools", TranscriptConcernType.PossibleRecognitionError, ALL, "loose stools"),
            ReviewRule("khasi", TranscriptConcernType.PossibleRecognitionError, ALL, "khansi"),
            ReviewRule("खासी", TranscriptConcernType.PossibleRecognitionError, HINDI, "खांसी"),
            ReviewRule("breathing difficulty", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("blood pressure", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("chest pain", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("dry cough", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("paracetamol", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("hypertension", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("diabetes", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("allergy", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("asthma", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("fever", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("cough", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("सांस लेने में परेशानी", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("सीने में दर्द", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("सूखी खांसी", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("पैरासिटामोल", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("रक्तचाप", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("मधुमेह", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("एलर्जी", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("बुखार", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("खांसी", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("दमा", TranscriptConcernType.MedicalTerm, HINDI),
            ReviewRule("seene mein dard", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("breathing problem", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("khaansi", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("khansi", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("bukhar", TranscriptConcernType.MedicalTerm, ALL),
            ReviewRule("saans", TranscriptConcernType.MedicalTerm, ALL)
        )
    }
}
