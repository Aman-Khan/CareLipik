package com.carelipik.app.domain.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptLabelProjectionTest {
    @Test
    fun labels_changeHeadersOnlyAndEditsRestoreStableIds() {
        val source = "Speaker 1: Patient: is part of this sentence.\n\nSpeaker 2: Synthetic reply"
        val projection = TranscriptLabelProjection(source,
            mapOf("speaker-1" to "Doctor", "speaker-2" to "Other (Person 2)"))
        assertEquals("Doctor: Patient: is part of this sentence.\n\nOther (Person 2): Synthetic reply", projection.text)
        assertEquals(source.replace("reply", "edited reply"),
            projection.restore(projection.text.replace("reply", "edited reply")))
    }

    @Test
    fun medicalTermOffsets_followChangedHeaderLengths() {
        val source = "Speaker 1: Synthetic medicine\nSpeaker 2: Synthetic response"
        val projection = TranscriptLabelProjection(source,
            mapOf("speaker-1" to "Synthetic doctor (Doctor)", "speaker-2" to "Patient"))
        val start = source.indexOf("medicine")
        assertEquals("medicine", projection.text.substring(projection.displayOffset(start),
            projection.displayOffset(start + "medicine".length)))
        assertEquals(source, projection.restore(projection.text))
    }
}
