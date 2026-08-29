package com.carelipik.app.domain.transcription

enum class TranscriptionLanguage(val displayName: String, val whisperCode: String) {
    Auto("Auto detect", ""),
    English("English", "en"),
    Hindi("Hindi", "hi"),
    Hinglish("Hinglish (Hindi + English)", "hi")
}
