package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.MedicalEntityType
import org.junit.Assert.assertEquals
import org.junit.Test

class ApolloNerDecoderTest {
    @Test
    fun decode_ignoresNonClinicalLabels() {
        val decoder = ApolloNerDecoder(listOf("O", "B-AGE"))
        assertEquals(emptyList<Any>(), decoder.decode("Patient", listOf(ApolloTokenPiece(5, 0, 7)),
            arrayOf(floatArrayOf(9f, 0f), floatArrayOf(0f, 9f), floatArrayOf(9f, 0f))))
    }

    @Test
    fun decode_mergesBioTokensIntoAnExactTranscriptSpan() {
        val decoder = ApolloNerDecoder(listOf("O", "B_DRUG", "I_DRUG"))
        val text = "metformin 500 mg"
        val pieces = listOf(
            ApolloTokenPiece(10, 0, 3),
            ApolloTokenPiece(11, 3, 9),
            ApolloTokenPiece(12, 10, 13),
            ApolloTokenPiece(13, 14, 16)
        )
        val logits = arrayOf(
            floatArrayOf(6f, 0f, 0f), // [CLS]
            floatArrayOf(0f, 6f, 0f),
            floatArrayOf(0f, 0f, 6f),
            floatArrayOf(6f, 0f, 0f),
            floatArrayOf(6f, 0f, 0f),
            floatArrayOf(6f, 0f, 0f) // [SEP]
        )

        val entities = decoder.decode(text, pieces, logits)

        assertEquals(1, entities.size)
        assertEquals("metformin", entities.single().text)
        assertEquals(MedicalEntityType.Medication, entities.single().type)
    }
}
