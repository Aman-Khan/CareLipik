package com.carelipik.app.domain.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishNameKeywordsTest {
    private val scores = mapOf("▁" to -10f, "A" to -9f, "L" to -9f, "I" to -9f,
        "C" to -9f, "E" to -9f, "▁A" to 0f, "LI" to -1f, "CE" to -2f)

    @Test fun encoding_mergesByModelScoreAndNormalizesCase() {
        assertEquals("▁A LI CE @name0", EnglishNameKeywords(scores).encode(" Alice "))
    }

    @Test fun encoding_repeatsNoDuplicateKeywords() {
        assertEquals("▁A LI CE @name0", EnglishNameKeywords(scores).encode("Alice Alice"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun encoding_blocksUnsupportedCharacters() { EnglishNameKeywords(scores).encode("राहुल") }

    @Test(expected = IllegalArgumentException::class)
    fun encoding_blocksInitials() { EnglishNameKeywords(scores).encode("A") }

    @Test(expected = IllegalArgumentException::class)
    fun encoding_blocksBlankNames() { EnglishNameKeywords(scores).encode("") }
}
