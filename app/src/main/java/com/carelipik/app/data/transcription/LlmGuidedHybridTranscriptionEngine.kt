package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.ConservativeWhisperUncertaintyDetector
import com.carelipik.app.domain.transcription.CorrectionStatus
import com.carelipik.app.domain.transcription.HybridCorrection
import com.carelipik.app.domain.transcription.HybridTranscriptMerger
import com.carelipik.app.domain.transcription.HybridTranscriptionConfig
import com.carelipik.app.domain.transcription.HybridTranscriptionReview
import com.carelipik.app.domain.transcription.LocalTranscriptAdvice
import com.carelipik.app.domain.transcription.LocalTranscriptAdvisor
import com.carelipik.app.domain.transcription.ProgressAwareTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptionStage
import com.carelipik.app.domain.transcription.UncertainSegment
import com.carelipik.app.domain.transcription.VerifiedAudioInterval
import com.carelipik.app.domain.transcription.ConsultationAwareTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionConsultationContext
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
import java.io.File
import kotlinx.coroutines.CancellationException

/** Each native session ends before the next model loads. All changes remain doctor suggestions. */
class LlmGuidedHybridTranscriptionEngine(
    private val whisper: SherpaWhisperTranscriptionEngine,
    private val medAsr: SherpaMedAsrTranscriptionEngine,
    private val advisor: LocalTranscriptAdvisor,
    private val config: HybridTranscriptionConfig = HybridTranscriptionConfig()
) : ConsultationAwareTranscriptionEngine {
    override val option = TranscriptionEngineOption.LlmGuidedHybrid

    override fun transcribe(audioPath: String, language: TranscriptionLanguage): TranscriptionResult =
        transcribe(audioPath, language, {}, {})

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage,
        onProgress: (TranscriptionStage) -> Unit,
        checkCancelled: () -> Unit
    ): TranscriptionResult = transcribeWithDetails(audioPath, language, onProgress, checkCancelled, {})

    override fun transcribeWithDetails(audioPath: String, language: TranscriptionLanguage,
        onProgress: (TranscriptionStage) -> Unit, checkCancelled: () -> Unit,
        onDetail: (String) -> Unit): TranscriptionResult = transcribeWithContext(audioPath, language,
            TranscriptionConsultationContext(), onProgress, checkCancelled, onDetail)

    override fun transcribeWithContext(audioPath: String, language: TranscriptionLanguage,
        context: TranscriptionConsultationContext, onProgress: (TranscriptionStage) -> Unit,
        checkCancelled: () -> Unit, onDetail: (String) -> Unit): TranscriptionResult {
        val started = android.os.SystemClock.elapsedRealtime()
        fun detail(message: String) { TranscriptionDiagnostics.event(message); onDetail(message) }
        onProgress(TranscriptionStage.Whisper)
        val samples: FloatArray
        val primary: WhisperPrimaryResult
        try {
            checkCancelled()
            detail("Reading recorded audio")
            samples = PcmWaveAudio.readMono16Khz(File(audioPath))
            val whisperLanguage = if (language == TranscriptionLanguage.Hinglish) TranscriptionLanguage.Auto else language
            primary = whisper.transcribeSamples(samples, whisperLanguage, checkCancelled,
                fallbackAfterEmptyDiarization = false, collectConfidence = true, onDetail = ::detail,
                diarizeAfterTranscription = true)
            detail("Whisper complete: ${primary.decodingSegments.size} regions; elapsed ${android.os.SystemClock.elapsedRealtime() - started}ms")
            TranscriptionDiagnostics.content(whisper.applicationContextForDiagnostics, "Whisper transcript", primary.result.transcript)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return TranscriptionResult.Failure(error.message ?: "Whisper transcription could not be completed.")
        }
        onProgress(TranscriptionStage.QwenAnalysis)
        val advice = try {
            val doctor = (primary.result.doctorVoiceMatch as? DoctorVoiceRoleMatchResult.Matched)
                ?.match?.doctorSpeakerId
            val roles = context.speakerRoles.toMutableMap()
            if (doctor != null) roles[doctor] = "Doctor"
            advisor.analyzeForConsultation(primary.decodingSegments, language,
                context.copy(speakerRoles = roles), checkCancelled, onDetail)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            checkCancelled()
            LocalTranscriptAdvice(notices = listOf("Qwen3 analysis failed; Whisper-confidence verification was used."))
        }
        checkCancelled()
        val notices = advice.notices.toMutableList()
        primary.backendNotice?.let { notices += it }
        if (primary.decodingSegments.any { !it.hasNativeConfidence }) {
            notices += "Decoder confidence was unavailable for some regions; timing/text checks were used there."
        }
        val confidenceFlags = ConservativeWhisperUncertaintyDetector(config).detect(primary.decodingSegments)
        val llmFlags = advice.findings.map { finding ->
            val segment = primary.decodingSegments[finding.segmentIndex]
            UncertainSegment(finding.segmentIndex, segment.startMs, segment.endMs,
                listOf("Qwen3 (${finding.priority}): ${finding.reason}"))
        }
        val uncertain = (confidenceFlags + llmFlags).groupBy { it.segmentIndex }.values.map { flags ->
            flags.first().copy(reasons = flags.flatMap { it.reasons }.distinct(),
                uncertainWords = flags.flatMap { it.uncertainWords }.distinct())
        }.sortedBy { it.startMs }
        val qwenCorrections = advice.findings.mapIndexed { index, finding ->
            val segment = primary.decodingSegments[finding.segmentIndex]
            HybridCorrection("qwen-$index", segment.startMs, segment.endMs, finding.original,
                finding.suggestion, finding.startIndex, finding.endIndex, segment.text, "",
                (listOf(finding.reason) + confidenceFlags.filter { it.segmentIndex == finding.segmentIndex }
                    .flatMap { it.reasons }).distinct(), CorrectionStatus.Suggested,
                qwenSuggestion = finding.suggestion, speakerId = segment.speakerId,
                wordIds = finding.wordIds, priority = finding.priority)
        }.toMutableList()
        val medCorrections = mutableListOf<HybridCorrection>()
        val verified = mutableListOf<VerifiedAudioInterval>()
        try {
            val extractor = AudioSegmentExtractor(config)
            val windows = extractor.plan(samples, PcmWaveAudio.sampleRate, primary.decodingSegments,
                uncertain, language, checkCancelled)
            detail("Selected ${windows.size} English MedASR regions from ${advice.findings.size} Qwen findings and ${confidenceFlags.size} Whisper uncertainty flags")
            if (windows.isEmpty()) {
                notices += "No regions met the English-language and verification limits; MedASR was not loaded. Qwen3 suggestions remain unverified."
            } else {
                onProgress(TranscriptionStage.MedAsrVerification)
                val merger = HybridTranscriptMerger(config)
                medAsr.withVerificationSession(checkCancelled) { recognize ->
                    windows.forEachIndexed { index, window ->
                        checkCancelled()
                        try {
                            detail("MedASR: verifying selected audio region ${index + 1}/${windows.size}")
                            val alternative = recognize(extractor.extract(samples, window)).trim()
                            TranscriptionDiagnostics.content(whisper.applicationContextForDiagnostics, "MedASR region ${index + 1}", alternative)
                            if (alternative.isBlank()) {
                                notices += "MedASR returned no text for a selected region; its Qwen3 suggestions remain unverified."
                            } else {
                                val startMs = window.startSample.toLong() * 1_000 / PcmWaveAudio.sampleRate
                                val endMs = window.endSample.toLong() * 1_000 / PcmWaveAudio.sampleRate
                                verified += VerifiedAudioInterval(startMs, endMs,
                                    (window.endSample - window.startSample + SherpaMedAsrTranscriptionEngine.MAX_CHUNK_SAMPLES - 1) /
                                        SherpaMedAsrTranscriptionEngine.MAX_CHUNK_SAMPLES)
                                val first = primary.decodingSegments[window.regionIndices.first()]
                                val last = primary.decodingSegments[window.regionIndices.last()]
                                val original = primary.result.transcript.substring(first.transcriptStartIndex, last.transcriptEndIndex)
                                val comparisons = merger.compare("llm-med-$index", startMs, endMs, original,
                                    first.transcriptStartIndex, alternative, window.reasons)
                                val covered = qwenCorrections.indices.filter { correctionIndex ->
                                    val correction = qwenCorrections[correctionIndex]
                                    correction.transcriptStartIndex!! >= first.transcriptStartIndex &&
                                        correction.transcriptEndIndex!! <= last.transcriptEndIndex
                                }
                                covered.forEach { correctionIndex ->
                                    val correction = qwenCorrections[correctionIndex]
                                    val aligned = comparisons.firstOrNull {
                                        it.status == CorrectionStatus.Suggested &&
                                            it.transcriptStartIndex == correction.transcriptStartIndex &&
                                            it.transcriptEndIndex == correction.transcriptEndIndex
                                    }
                                    val expected = original.replaceRange(
                                        correction.transcriptStartIndex!! - first.transcriptStartIndex,
                                        correction.transcriptEndIndex!! - first.transcriptStartIndex, correction.suggestedText)
                                    val agree = when {
                                        normalize(expected) == normalize(alternative) -> true
                                        aligned != null -> normalize(aligned.suggestedText) == normalize(correction.suggestedText)
                                        normalize(original) == normalize(alternative) -> false
                                        else -> null
                                    }
                                    qwenCorrections[correctionIndex] = correction.copy(medAsrAlternative = alternative,
                                        medAsrSuggestedText = aligned?.suggestedText, modelsAgree = agree)
                                }
                                // A precisely aligned alternative is offered on the same card. Retain other
                                // MedASR findings, including unsafe alignments, for independent doctor review.
                                medCorrections += comparisons.filter { comparison -> covered.none { correctionIndex ->
                                    val correction = qwenCorrections[correctionIndex]
                                    comparison.transcriptStartIndex == correction.transcriptStartIndex &&
                                        comparison.transcriptEndIndex == correction.transcriptEndIndex
                                } }.map { it.copy(speakerId = first.speakerId) }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            notices += "MedASR could not verify a selected region; Qwen3 suggestions remain unverified."
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            checkCancelled()
            notices += "MedASR verification was unavailable; Qwen3 suggestions remain available for doctor review."
        }
        checkCancelled()
        onProgress(TranscriptionStage.Comparing)
        detail("Review ready: ${qwenCorrections.size + medCorrections.size} suggestions; total ${android.os.SystemClock.elapsedRealtime() - started}ms")
        return primary.result.copy(hybridReview = HybridTranscriptionReview(primary.result.transcript,
            primary.decodingSegments, uncertain, qwenCorrections + medCorrections, notices.distinct(),
            verified, isLlmGuided = true))
    }

    private fun normalize(text: String): String = text.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
