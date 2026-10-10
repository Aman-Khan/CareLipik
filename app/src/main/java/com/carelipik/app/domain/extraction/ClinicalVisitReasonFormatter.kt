package com.carelipik.app.domain.extraction

/** Keeps a visit reason focused on the patient's complaint without inventing clinical facts. */
object ClinicalVisitReasonFormatter {
    fun concise(value: String): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return ""

        val clinicalSentences = SENTENCE_BOUNDARY.split(normalized)
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(::isAdministrativeSentence)
            .take(MAX_SENTENCES)

        return clinicalSentences.joinToString(" ")
            .ifBlank { normalized }
            .take(MAX_CHARACTERS)
            .trim()
    }

    private fun isAdministrativeSentence(sentence: String): Boolean {
        val words = sentence.split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.size <= 2 && sentence.none(Char::isDigit)) return true
        return COURTESY_ONLY.matches(sentence) ||
            CLINICIAN_HANDOFF.containsMatchIn(sentence) ||
            STAFF_ACTION.containsMatchIn(sentence)
    }

    private const val MAX_SENTENCES = 4
    private const val MAX_CHARACTERS = 420
    private val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+")
    private val COURTESY_ONLY = Regex(
        "^(?:thanks?|thank you|hello|hi|good (?:morning|afternoon|evening))(?:[,!. ]+[a-z .'-]+)?[.!]?$",
        RegexOption.IGNORE_CASE
    )
    private val CLINICIAN_HANDOFF = Regex(
        "\\b(?:dr\\.?|doctor)\\s+[a-z.'-]+\\s+(?:will|should)\\s+(?:be|join|see)\\b|" +
            "\\b(?:will|should)\\s+be\\s+(?:in|with you)\\s+(?:in\\s+)?(?:a|one|few|just)?\\s*" +
            "(?:moment|minute|shortly)\\b",
        RegexOption.IGNORE_CASE
    )
    private val STAFF_ACTION = Regex(
        "^(?:i(?:'ll| will)|we(?:'ll| will))\\s+(?:get|bring|place|ask|call|help|check)\\b",
        RegexOption.IGNORE_CASE
    )
}
