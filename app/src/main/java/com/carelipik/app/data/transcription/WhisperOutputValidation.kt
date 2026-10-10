package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.TranscriptionLanguage

internal class WhisperOutputRejectedException(message: String) : IllegalArgumentException(message)

/** Reject visibly corrupt output; language selection alone cannot guarantee valid decoding. */
internal object WhisperOutputValidation {
    fun requireReadable(text: String, language: TranscriptionLanguage) {
        if (text.any { it == '\uFFFD' || (it.isISOControl() && !it.isWhitespace()) })
            throw WhisperOutputRejectedException("Whisper returned invalid text encoding")
        val visible = text.filterNot(Char::isWhitespace)
        if (visible.isEmpty()) return
        val letters = visible.count(Char::isLetter)
        val symbols = visible.count { !it.isLetterOrDigit() && it !in ".,?!:;'’\"()-/%+&" }
        if (symbols > maxOf(2, visible.length / 5))
            throw WhisperOutputRejectedException("Whisper returned symbol-heavy text")
        if (language == TranscriptionLanguage.English && letters >= 4) {
            val latin = visible.count { it.isLetter() && Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }
            if (latin < letters * 0.85)
                throw WhisperOutputRejectedException("Whisper output did not match the selected English language")
        }
    }
}
