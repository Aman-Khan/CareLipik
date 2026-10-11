package com.carelipik.app.domain.transcription

/** Removes known name components without matching inside other words. */
object PatientNameTranscriptRedactor {
    fun redact(transcript: String, patientName: String): String {
        val parts = Regex("[\\p{L}\\p{M}]+").findAll(patientName)
            .map { it.value }.filter { it.length >= 2 }.toList()
        if (parts.isEmpty()) return transcript
        val fullName = parts.joinToString("[\\s.'-]+") { Regex.escape(it) }
        val alternatives = (listOf(fullName) + parts.distinct().sortedByDescending { it.length }
            .map { Regex.escape(it) }).joinToString("|")
        return Regex("(?<![\\p{L}\\p{M}])(?:$alternatives)(?![\\p{L}\\p{M}])",
            RegexOption.IGNORE_CASE).replace(transcript, "Patient")
    }
}
