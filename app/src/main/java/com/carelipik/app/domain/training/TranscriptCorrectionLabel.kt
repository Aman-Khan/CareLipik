package com.carelipik.app.domain.training

data class TranscriptCorrectionLabel(
    val id: String,
    val createdAtMillis: Long,
    val audioClipReference: String,
    val asrText: String,
    val correctedText: String,
    val originalTerm: String,
    val correctedTerm: String,
    val language: String,
    val transcriptionEngine: String,
    val confirmedByDoctor: Boolean = true,
    val schemaVersion: Int = 1
)
