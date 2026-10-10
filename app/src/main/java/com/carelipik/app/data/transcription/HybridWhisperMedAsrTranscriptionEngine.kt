package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.ConservativeWhisperUncertaintyDetector
import com.carelipik.app.domain.transcription.HybridTranscriptMerger
import com.carelipik.app.domain.transcription.HybridTranscriptionConfig
import com.carelipik.app.domain.transcription.HybridTranscriptionReview
import com.carelipik.app.domain.transcription.ProgressAwareTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptionStage
import com.carelipik.app.domain.transcription.VerifiedAudioInterval
import java.io.File
import kotlinx.coroutines.CancellationException

/** Whisper is authoritative. MedASR only sees bounded, uncertain, English-compatible PCM windows. */
class HybridWhisperMedAsrTranscriptionEngine(
    private val whisper: SherpaWhisperTranscriptionEngine,
    private val medAsr: SherpaMedAsrTranscriptionEngine,
    private val config: HybridTranscriptionConfig = HybridTranscriptionConfig()
) : ProgressAwareTranscriptionEngine {
    override val option = TranscriptionEngineOption.WhisperMedAsrHybrid

    override fun transcribe(audioPath: String, language: TranscriptionLanguage): TranscriptionResult =
        transcribe(audioPath, language, {}, {})

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage,
        onProgress: (TranscriptionStage) -> Unit,
        checkCancelled: () -> Unit
    ): TranscriptionResult {
        onProgress(TranscriptionStage.Whisper)
        val samples: FloatArray
        val primary: WhisperPrimaryResult
        try {
            checkCancelled()
            samples = PcmWaveAudio.readMono16Khz(File(audioPath))
            // Auto language lets Whisper identify English turns within a Hindi-English consultation.
            val whisperLanguage = if (language == TranscriptionLanguage.Hinglish) TranscriptionLanguage.Auto else language
            primary = whisper.transcribeSamples(samples, whisperLanguage, checkCancelled, fallbackAfterEmptyDiarization = false, collectConfidence = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return TranscriptionResult.Failure(error.message ?: "Whisper transcription could not be completed.")
        }

        val notices = mutableListOf<String>()
        if (primary.decodingSegments.any { !it.hasNativeConfidence }) {
            notices += "Decoder confidence was unavailable for some regions; only observable timing/text checks were used there."
        }
        val uncertain = ConservativeWhisperUncertaintyDetector(config).detect(primary.decodingSegments)
        val review = HybridTranscriptionReview(primary.result.transcript, primary.decodingSegments, uncertain)
        try {
            val extractor = AudioSegmentExtractor(config)
            val windows = extractor.plan(samples, PcmWaveAudio.sampleRate, primary.decodingSegments, uncertain, language, checkCancelled)
            if (primary.decodingSegments.isEmpty() || primary.decodingSegments.any { it.transcriptStartIndex < 0 }) {
                notices += "Whisper segment alignment was unavailable; affected regions were not verified."
            }
            if (windows.isEmpty()) {
                notices += if (uncertain.isEmpty()) "No uncertain regions were identified; MedASR was not loaded."
                    else "Uncertain regions did not meet English-language, duration, silence, or verification-budget limits. Whisper was preserved."
                return primary.result.copy(hybridReview = review.copy(notices = notices))
            }
            onProgress(TranscriptionStage.MedAsrVerification)
            val merger = HybridTranscriptMerger(config)
            val verified = mutableListOf<VerifiedAudioInterval>()
            val corrections = medAsr.withVerificationSession(checkCancelled) { recognize ->
                windows.flatMapIndexed { index, window ->
                    checkCancelled()
                    try {
                        verified += VerifiedAudioInterval(
                            window.startSample.toLong() * 1_000 / PcmWaveAudio.sampleRate,
                            window.endSample.toLong() * 1_000 / PcmWaveAudio.sampleRate,
                            ((window.endSample - window.startSample + SherpaMedAsrTranscriptionEngine.MAX_CHUNK_SAMPLES - 1) /
                                SherpaMedAsrTranscriptionEngine.MAX_CHUNK_SAMPLES)
                        )
                        val alternative = recognize(extractor.extract(samples, window)).trim()
                        if (alternative.isBlank()) {
                            notices += "MedASR returned no text for a selected region; Whisper was preserved."
                            emptyList()
                        } else {
                            val first = primary.decodingSegments[window.regionIndices.first()]
                            val last = primary.decodingSegments[window.regionIndices.last()]
                            val original = primary.result.transcript.substring(first.transcriptStartIndex, last.transcriptEndIndex)
                            merger.compare(
                                id = "hybrid-$index",
                                startMs = window.startSample.toLong() * 1_000 / PcmWaveAudio.sampleRate,
                                endMs = window.endSample.toLong() * 1_000 / PcmWaveAudio.sampleRate,
                                whisperText = original,
                                whisperStartIndex = first.transcriptStartIndex,
                                medAsrText = alternative,
                                reasons = window.reasons
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        notices += "MedASR could not verify a selected region; Whisper was preserved."
                        emptyList()
                    }
                }
            }
            checkCancelled()
            onProgress(TranscriptionStage.Comparing)
            return primary.result.copy(hybridReview = review.copy(corrections = corrections, notices = notices.distinct(), verifiedAudio = verified))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            notices += "MedASR verification was unavailable; the original Whisper transcript is retained."
            return primary.result.copy(hybridReview = review.copy(notices = notices.distinct()))
        }
    }

}
