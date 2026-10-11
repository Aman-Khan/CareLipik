package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.TranscriptSegmentParser

/** Supports Doctor, Patient and numbered Speaker/Person labels in a transcript. */
class LabelledTranscriptSegmentParser : TranscriptSegmentParser {
    override fun parse(transcript: String): List<TranscriptSegment> {
        val segments = mutableListOf<TranscriptSegment>()
        var currentSpeakerId: String? = null
        var currentText = StringBuilder()

        fun flushSegment() {
            val speakerId = currentSpeakerId
            val text = currentText.toString().trim()
            if (speakerId != null && text.isNotBlank()) {
                segments += TranscriptSegment(speakerId, text)
            }
            currentSpeakerId = null
            currentText = StringBuilder()
        }

        transcript.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val match = labelledLine.matchEntire(line)
            if (match != null) {
                flushSegment()
                currentSpeakerId = normalizedSpeakerId(match.groupValues[1].trim())
                currentText.append(match.groupValues[2].trim())
            } else if (line.isNotBlank() && currentSpeakerId != null) {
                if (currentText.isNotEmpty()) currentText.append(' ')
                currentText.append(line)
            }
        }
        flushSegment()
        return segments
    }

    private fun normalizedSpeakerId(label: String): String = when {
        label.equals("doctor", ignoreCase = true) -> DOCTOR_ID
        label.equals("patient", ignoreCase = true) -> PATIENT_ID
        else -> label.lowercase().replace(Regex("^person\\s+"), "speaker ").replace(whitespace, "-")
    }

    private companion object {
        const val DOCTOR_ID = "doctor"
        const val PATIENT_ID = "patient"
        val whitespace = Regex("\\s+")
        val labelledLine = Regex(
            "^(Doctor|Patient|(?:Speaker|Person)\\s+[A-Za-z0-9._-]+)\\s*:\\s*(.+)$",
            RegexOption.IGNORE_CASE
        )
    }
}
