package com.carelipik.app.domain.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class ClinicalNoteContentFormatterTest {
    @Test
    fun clean_removesSpeakerLabelsCourtesyStaffActionAndHandoff() {
        val content = "Patient: Thanks, Sunita. I twisted my ankle this morning. " +
            "Other: I'll place an ice pack now. Dr Vikram will be in shortly."

        assertEquals(
            "I twisted my ankle this morning.",
            ClinicalNoteContentFormatter.clean(content)
        )
    }
}
