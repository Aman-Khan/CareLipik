package com.carelipik.app.domain.prescription

object LetterheadMedia {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun mimeType(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Letterhead must be at most 8 MB." }
        fun starts(vararg values: Int) = bytes.size >= values.size && values.indices.all { (bytes[it].toInt() and 255) == values[it] }
        return when {
            bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-" -> "application/pdf"
            starts(255, 216, 255) -> "image/jpeg"
            starts(137, 80, 78, 71, 13, 10, 26, 10) -> "image/png"
            bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> error("Choose a PDF, JPEG, PNG or WebP letterhead.")
        }
    }
}
