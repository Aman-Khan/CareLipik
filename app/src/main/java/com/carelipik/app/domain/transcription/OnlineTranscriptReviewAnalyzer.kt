package com.carelipik.app.domain.transcription

sealed interface OnlineTranscriptReviewResult {
    data class Success(
        val concerns: List<TranscriptConcern>,
        val sourceName: String,
        val codesVerified: Boolean
    ) : OnlineTranscriptReviewResult

    data class Failure(val message: String) : OnlineTranscriptReviewResult
}

/** Provider-neutral boundary for consented online clinical term extraction. */
fun interface OnlineTranscriptReviewAnalyzer {
    fun analyze(
        transcript: String,
        language: TranscriptionLanguage
    ): OnlineTranscriptReviewResult
}
