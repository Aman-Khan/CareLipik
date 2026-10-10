package com.carelipik.app.data.transcription

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ApolloSentencePieceTokenizerTest {
    @Test
    fun encode_keepsOriginalTranscriptOffsets() {
        val tokenizer = ApolloSentencePieceTokenizer(TOKENIZER)

        val encoded = tokenizer.encode("metformin", maximumTokens = 12)

        assertArrayEquals(longArrayOf(1, 3, 4, 2), encoded.ids)
        assertEquals(0, encoded.pieces[0].startIndex)
        assertEquals(3, encoded.pieces[0].endIndexExclusive)
        assertEquals(3, encoded.pieces[1].startIndex)
        assertEquals(9, encoded.pieces[1].endIndexExclusive)
    }

    private companion object {
        const val TOKENIZER = """{
          "model": {"vocab": [
            ["[PAD]", 0.0], ["[CLS]", 0.0], ["[SEP]", 0.0], ["▁met", 5.0], ["formin", 4.0], ["[UNK]", -1.0]
          ]}
        }"""
    }
}
