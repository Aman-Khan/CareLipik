package com.carelipik.app.domain.transcription

enum class TranscriptConcernType {
    PossibleRecognitionError,
    MedicalTerm
}

data class TranscriptConcern(
    val id: String,
    val text: String,
    val startIndex: Int,
    val endIndexExclusive: Int,
    val type: TranscriptConcernType,
    val reason: String,
    val suggestedReplacement: String? = null
)

/** Finds transcript text that should be explicitly checked by the doctor. */
fun interface TranscriptReviewAnalyzer {
    fun analyze(
        transcript: String,
        language: TranscriptionLanguage
    ): List<TranscriptConcern>
}
