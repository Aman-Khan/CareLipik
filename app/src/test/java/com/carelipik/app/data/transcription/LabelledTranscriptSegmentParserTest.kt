package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelledTranscriptSegmentParserTest {
    private val parser = LabelledTranscriptSegmentParser()

    @Test
    fun personLabels_preserveStableSpeakerIdsBeyondTwo() {
        assertEquals(
            listOf(TranscriptSegment("speaker-3", "Third reply"), TranscriptSegment("speaker-12", "Later reply")),
            parser.parse("Person 3: Third reply\nPerson 12: Later reply")
        )
    }

    @Test
    fun parse_preservesExplicitSpeakerOrder() {
        val segments = parser.parse(
            "Speaker 1: Good morning\n\n" +
                "Speaker 2: I have a question\n\n" +
                "Speaker 1: Please continue"
        )

        assertEquals(
            listOf(
                TranscriptSegment("speaker-1", "Good morning"),
                TranscriptSegment("speaker-2", "I have a question"),
                TranscriptSegment("speaker-1", "Please continue")
            ),
            segments
        )
    }

    @Test
    fun parse_doesNotInventSegmentsForUnlabelledText() {
        assertEquals(emptyList<TranscriptSegment>(), parser.parse("A continuous transcript"))
    }

    @Test
    fun parse_recognizesConfirmedDoctorAndPatientLabels() {
        assertEquals(
            listOf(
                TranscriptSegment("doctor", "How are you?"),
                TranscriptSegment("patient", "I am better.")
            ),
            parser.parse("Doctor: How are you?\nPatient: I am better.")
        )
    }

    @Test
    fun parse_keepsWrappedTextWithItsSpeaker() {
        assertEquals(
            listOf(
                TranscriptSegment("speaker-1", "First line continues here"),
                TranscriptSegment("speaker-2", "Reply")
            ),
            parser.parse("Speaker 1: First line\ncontinues here\nSpeaker 2: Reply")
        )
    }
}
