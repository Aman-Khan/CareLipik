package com.carelipik.app.domain.extraction

/** Removes conversational scaffolding while preserving explicitly stated clinical facts. */
object ClinicalNoteContentFormatter {
    fun clean(value: String): String = value
        .replace(SPEAKER_LABEL, "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { normalized ->
            if (normalized.isBlank()) return ""
            SENTENCE_BOUNDARY.split(normalized)
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot(::isAdministrative)
                .joinToString(" ")
        }

    private fun isAdministrative(sentence: String): Boolean =
        COURTESY.matches(sentence) ||
            HANDOFF.containsMatchIn(sentence) ||
            STAFF_ACTION.matches(sentence)

    private val SPEAKER_LABEL = Regex(
        "(?i)(?:^|(?<=[.!?]\\s))(?:doctor|patient|other|noise|speaker\\s+[a-z0-9._-]+)\\s*:\\s*"
    )
    private val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+")
    private val COURTESY = Regex(
        "^(?:thanks?|thank you|hello|hi|good (?:morning|afternoon|evening))(?:[,!. ]+[a-z .'-]+)?[.!]?$",
        RegexOption.IGNORE_CASE
    )
    private val HANDOFF = Regex(
        "\\b(?:dr\\.?|doctor)\\s+[a-z.'-]+\\s+(?:will|should)\\s+(?:be|join|see)\\b|" +
            "\\b(?:will|should)\\s+be\\s+(?:in|with you)\\b",
        RegexOption.IGNORE_CASE
    )
    private val STAFF_ACTION = Regex(
        "^(?:i(?:'ll| will)|we(?:'ll| will))\\s+(?:get|bring|place|ask|call|help|check)\\b.*",
        RegexOption.IGNORE_CASE
    )
}
