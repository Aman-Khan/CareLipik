package com.carelipik.app.domain.prescription

import org.junit.Assert.*
import org.junit.Test

class LetterheadMediaTest {
    @Test fun detectsPdfAndSupportedImageSignatures() {
        assertEquals("application/pdf", LetterheadMedia.mimeType("%PDF-1.7".toByteArray()))
        assertEquals("image/jpeg", LetterheadMedia.mimeType(byteArrayOf(-1, -40, -1)))
        assertEquals("image/png", LetterheadMedia.mimeType(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        assertEquals("image/webp", LetterheadMedia.mimeType("RIFF0000WEBP".toByteArray()))
    }
    @Test fun rejectsUnsupportedAndOversizedAttachments() {
        assertThrows(IllegalStateException::class.java) { LetterheadMedia.mimeType("fake image".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { LetterheadMedia.mimeType(ByteArray(LetterheadMedia.MAX_BYTES + 1)) }
    }
}
