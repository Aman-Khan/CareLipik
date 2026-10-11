package com.carelipik.app.domain.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class ClinicalVisitReasonFormatterTest {
    @Test
    fun concise_removesCourtesyHandoffAndStaffActionsWithoutChangingClinicalFacts() {
        val raw = "Thanks, Sunita. I missed the last step on my porch this morning and twisted " +
            "my ankle outward. It swelled up quickly. I cannot rotate the ankle. " +
            "I'll place an ice pack to help. Dr Vikram will be in, in just a moment."

        assertEquals(
            "I missed the last step on my porch this morning and twisted my ankle outward. " +
                "It swelled up quickly. I cannot rotate the ankle.",
            ClinicalVisitReasonFormatter.concise(raw)
        )
    }

    @Test
    fun concise_preservesShortClinicalReason() {
        assertEquals(
            "Cough and fever for four days.",
            ClinicalVisitReasonFormatter.concise("Cough and fever for four days.")
        )
    }
}
