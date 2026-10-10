package com.carelipik.app.domain.transcription

data class LocalWordReference(val id: String, val text: String, val start: Int, val end: Int)

data class LocalTranscriptFinding(
    val segmentIndex: Int,
    val wordIds: List<String>,
    val original: String,
    val suggestion: String,
    val reason: String,
    val priority: String,
    val startIndex: Int,
    val endIndex: Int
)

data class LocalTranscriptAdvice(
    val findings: List<LocalTranscriptFinding> = emptyList(),
    val notices: List<String> = emptyList()
)

/** Advice may flag existing text only. Audio times remain owned by Whisper. */
fun interface LocalTranscriptAdvisor {
    fun analyze(segments: List<WhisperDecodingSegment>, checkCancelled: () -> Unit): LocalTranscriptAdvice
    fun analyzeWithProgress(segments: List<WhisperDecodingSegment>, checkCancelled: () -> Unit,
        onDetail: (String) -> Unit): LocalTranscriptAdvice = analyze(segments, checkCancelled)
    fun analyzeForLanguage(segments: List<WhisperDecodingSegment>, language: TranscriptionLanguage,
        checkCancelled: () -> Unit, onDetail: (String) -> Unit): LocalTranscriptAdvice =
        analyzeWithProgress(segments, checkCancelled, onDetail)
    fun analyzeForConsultation(segments: List<WhisperDecodingSegment>, language: TranscriptionLanguage,
        context: TranscriptionConsultationContext, checkCancelled: () -> Unit,
        onDetail: (String) -> Unit): LocalTranscriptAdvice =
        analyzeForLanguage(segments, language, checkCancelled, onDetail)
}
