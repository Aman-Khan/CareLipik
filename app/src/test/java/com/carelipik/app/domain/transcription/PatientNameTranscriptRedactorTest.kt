package com.carelipik.app.domain.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class PatientNameTranscriptRedactorTest {
    @Test fun redactsFullNameAndSeparateComponents() {
        assertEquals("Patient, Patient and Patient.",
            PatientNameTranscriptRedactor.redact("ALICE   EXAMPLE, Alice and Example.", "Alice Example"))
    }
    @Test fun preservesOtherWordsAndMedicines() {
        assertEquals("Aliceville takes paracetamol. Patient feels better.",
            PatientNameTranscriptRedactor.redact("Aliceville takes paracetamol. Alice feels better.", "Alice Example"))
    }
    @Test fun punctuationBetweenNameComponents_isRedactedTogether() {
        assertEquals("Hello Patient!", PatientNameTranscriptRedactor.redact("Hello Alice-Example!", "Alice Example"))
    }
    @Test fun blankName_preservesTranscript() {
        assertEquals("Reviewed speech", PatientNameTranscriptRedactor.redact("Reviewed speech", ""))
    }
}
