package com.carelipik.app.domain.transcription

/** Experimental probability thresholds and inference limits. Times refer to the original recording. */
data class HybridTranscriptionConfig(
    val repetitionCount: Int = 3,
    val compressionRatioThreshold: Float = 2.4f,
    val sparseSpeechSeconds: Float = 8f,
    val contextPaddingMs: Long = 400,
    val mergeGapMs: Long = 250,
    val minimumSegmentMs: Long = 500,
    val maximumSegmentMs: Long = 30_000,
    val maximumMedAsrCalls: Int = 4,
    val maximumAudioFraction: Float = 0.25f,
    val minimumRms: Float = 0.002f,
    val maximumChangedWords: Int = 4,
    val minimumMatchingWords: Int = 3,
    val minimumAlignmentFraction: Float = 0.6f,
    val wordProbabilityThreshold: Float = 0.55f,
    val minimumTokenProbabilityThreshold: Float = 0.2f
) {
    init {
        require(repetitionCount >= 2 && compressionRatioThreshold > 1f)
        require(sparseSpeechSeconds > 0f && contextPaddingMs >= 0 && mergeGapMs >= 0)
        require(minimumSegmentMs > 0 && maximumSegmentMs >= minimumSegmentMs)
        require(maximumMedAsrCalls > 0 && maximumAudioFraction > 0f && maximumAudioFraction < 1f)
        require(minimumRms > 0f && maximumChangedWords > 0 && minimumMatchingWords > 0)
        require(minimumAlignmentFraction in 0f..1f)
        require(wordProbabilityThreshold > 0f && wordProbabilityThreshold < 1f)
        require(minimumTokenProbabilityThreshold > 0f && minimumTokenProbabilityThreshold < 1f)
    }
}

data class WhisperDecodingSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val transcriptStartIndex: Int,
    val transcriptEndIndex: Int,
    val speakerId: String?,
    val language: String?,
    val tokens: List<String> = emptyList(),
    val tokenStartTimesMs: List<Long> = emptyList(),
    val tokenDurationsMs: List<Long> = emptyList(),
    val rawTokenTimestampsSeconds: List<Float> = emptyList(),
    val rawTokenDurationsSeconds: List<Float> = emptyList(),
    val emotion: String? = null,
    val event: String? = null,
    val tokenLogProbabilities: List<Float> = emptyList(),
    val wordConfidences: List<WhisperWordConfidence> = emptyList(),
    val hasNativeConfidence: Boolean = false
)

/** Character ranges are local to the decoding segment. Scores are not calibrated accuracy. */
data class WhisperWordConfidence(
    val startIndex: Int,
    val endIndex: Int,
    val text: String,
    val probability: Float,
    val minimumTokenProbability: Float
)

data class UncertainSegment(
    val segmentIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val reasons: List<String>,
    val uncertaintyScore: Float? = null,
    val uncertainWords: List<WhisperWordConfidence> = emptyList()
)

enum class CorrectionStatus {
    Suggested,
    Accepted,
    Rejected,
    Unresolved
}

data class HybridCorrection(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val originalText: String,
    val suggestedText: String,
    val transcriptStartIndex: Int?,
    val transcriptEndIndex: Int?,
    val whisperContext: String,
    val medAsrAlternative: String,
    val reasons: List<String>,
    val status: CorrectionStatus
)

data class HybridTranscriptionReview(
    val originalWhisperTranscript: String,
    val whisperSegments: List<WhisperDecodingSegment>,
    val uncertainSegments: List<UncertainSegment>,
    val corrections: List<HybridCorrection> = emptyList(),
    val notices: List<String> = emptyList(),
    val verifiedAudio: List<VerifiedAudioInterval> = emptyList()
)

data class VerifiedAudioInterval(val startMs: Long, val endMs: Long, val calls: Int)

enum class TranscriptionStage(val displayName: String) {
    Whisper("Transcribing with Whisper on this device…"),
    MedAsrVerification("Checking selected English audio with MedASR…"),
    Comparing("Comparing Whisper and MedASR suggestions…")
}

/** Progress and cancellation belong to the request, never to a shared recognizer instance. */
interface ProgressAwareTranscriptionEngine : AudioTranscriptionEngine {
    fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage,
        onProgress: (TranscriptionStage) -> Unit,
        checkCancelled: () -> Unit
    ): TranscriptionResult
}
