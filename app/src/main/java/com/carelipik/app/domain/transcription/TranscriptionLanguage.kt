package com.carelipik.app.domain.transcription

enum class TranscriptionLanguage(val displayName: String, val whisperCode: String) {
    Auto("Auto detect", ""),
    English("English", "en"),
    HindiHinglish("Hindi / Hinglish", "hi")
}
