package com.carelipik.app.domain.transcription

/** Parses explicit speaker-labelled text without guessing who spoke unlabelled text. */
fun interface TranscriptSegmentParser {
    fun parse(transcript: String): List<TranscriptSegment>
}
