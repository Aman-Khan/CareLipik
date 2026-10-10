package com.carelipik.app.data.clinical

import com.carelipik.app.domain.clinical.ClinicalTransliteration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetPreservingClinicalTextNormalizerTest {
    private val normalizer = OffsetPreservingClinicalTextNormalizer()

    @Test
    fun normalization_preservesOriginalTextAndMapsCollapsedWhitespace() {
        val original = "  METFORMIN\t 500 mg\nTwice daily  "

        val result = normalizer.normalize(original)

        assertEquals(original, result.originalText)
        assertEquals("metformin 500 mg twice daily", result.lookupText)
        val metforminRange = result.originalRange(0, "metformin".length)
        assertEquals("METFORMIN", original.substring(metforminRange.first, metforminRange.last + 1))
        val strengthStart = result.lookupText.indexOf("500 mg")
        val strengthRange = result.originalRange(strengthStart, strengthStart + "500 mg".length)
        assertEquals("500 mg", original.substring(strengthRange.first, strengthRange.last + 1))
    }

    @Test
    fun normalization_canonicalizesPunctuationWithoutChangingSourceOffsets() {
        val original = "Patient’s cough—dry"

        val result = normalizer.normalize(original)

        assertEquals("patient's cough-dry", result.lookupText)
        val normalizedCoughStart = result.lookupText.indexOf("cough")
        val originalRange = result.originalRange(
            normalizedCoughStart,
            normalizedCoughStart + "cough".length
        )
        assertEquals("cough", original.substring(originalRange.first, originalRange.last + 1))
    }

    @Test
    fun normalization_preservesDevanagariForFutureIndicModel() {
        val original = "मुझे  तीन दिन से खाँसी है"

        val result = normalizer.normalize(original)

        assertEquals("मुझे तीन दिन से खाँसी है", result.lookupText)
        assertTrue(result.transliterations.isEmpty())
    }

    @Test
    fun transliterationCandidates_areKeptSeparateAndInvalidOffsetsAreRejected() {
        val original = "मुझे खांसी है"
        val normalizerWithCandidates = OffsetPreservingClinicalTextNormalizer {
            listOf(
                ClinicalTransliteration(5, 10, "khansi", "hi-Latn", 0.92f),
                ClinicalTransliteration(-1, 2, "invalid", "hi-Latn", 1f)
            )
        }

        val result = normalizerWithCandidates.normalize(original)

        assertEquals(original, result.lookupText)
        assertEquals(
            listOf(ClinicalTransliteration(5, 10, "khansi", "hi-Latn", 0.92f)),
            result.transliterations
        )
    }

    @Test
    fun emptyInput_returnsEmptyMappedText() {
        val result = normalizer.normalize("")

        assertEquals("", result.lookupText)
        assertTrue(result.originalOffsets.isEmpty())
    }
}
