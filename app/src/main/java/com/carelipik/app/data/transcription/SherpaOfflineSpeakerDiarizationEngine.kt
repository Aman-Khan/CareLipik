package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.domain.transcription.DiarizedAudioTurn
import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
import com.carelipik.app.domain.transcription.SpeakerDiarizationResult
import com.carelipik.app.domain.transcription.SpeakerIdentityResolver
import com.carelipik.app.domain.transcription.FixedCountVoiceClusterer
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import kotlin.math.ceil
import kotlin.math.floor

/** On-device diarization; a negative speaker count enables automatic clustering. */
class SherpaOfflineSpeakerDiarizationEngine(
    private val context: Context
) : SpeakerDiarizationEngine {
    override fun diarize(
        samples: FloatArray,
        sampleRate: Int,
        expectedSpeakerCount: Int
    ): SpeakerDiarizationResult {
        if (!hasModels()) {
            return SpeakerDiarizationResult.Unavailable(
                "Offline speaker models are not installed in this build."
            )
        }
        if (samples.isEmpty()) {
            return SpeakerDiarizationResult.Failure("The recording contains no audio.")
        }
        return runCatching {
            val turns = createDiarizer(expectedSpeakerCount).useDiarizer { diarizer ->
                require(sampleRate == diarizer.sampleRate()) {
                    "Speaker detection requires ${diarizer.sampleRate()} Hz audio."
                }
                diarizer.process(samples)
                    .map { segment ->
                        DiarizedAudioTurn(
                            speakerId = "speaker-${segment.speaker + 1}",
                            startSeconds = segment.start,
                            endSeconds = segment.end
                        )
                    }
                    .normalizeTurns(samples.size / sampleRate.toFloat())
            }
            if (expectedSpeakerCount > 0 && turns.map { it.speakerId }.distinct().size != expectedSpeakerCount) {
                recoverFixedCount(samples, sampleRate, turns, expectedSpeakerCount)
            } else if (expectedSpeakerCount <= 0 && turns.map { it.speakerId }.distinct().size > 1) {
                reconcileIdentities(samples, sampleRate, turns)
            } else {
                turns
            }
        }.fold(
            onSuccess = { turns ->
                if (turns.isEmpty()) {
                    SpeakerDiarizationResult.Failure("No speaker turns were detected.")
                } else {
                    SpeakerDiarizationResult.Success(turns)
                }
            },
            onFailure = { error ->
                SpeakerDiarizationResult.Failure(
                    error.message ?: "Offline speaker detection could not be completed."
                )
            }
        )
    }

    private fun recoverFixedCount(
        samples: FloatArray,
        sampleRate: Int,
        turns: List<DiarizedAudioTurn>,
        speakerCount: Int
    ): List<DiarizedAudioTurn> {
        val extractor = SpeakerEmbeddingExtractor(context.assets, SpeakerEmbeddingExtractorConfig(
            model = "$MODEL_DIR/$EMBEDDING_MODEL",
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
            debug = false,
            provider = "cpu"
        ))
        return try {
            val windows = turns.sortedBy { it.startSeconds }.flatMap { turn ->
                val duration = turn.endSeconds - turn.startSeconds
                val count = ceil(duration / 2f).toInt().coerceAtLeast(1)
                (0 until count).map { index ->
                    turn.copy(
                        startSeconds = turn.startSeconds + duration * index / count,
                        endSeconds = turn.startSeconds + duration * (index + 1) / count
                    )
                }
            }
            val evidence = windows.map { window ->
                val start = floor(window.startSeconds * sampleRate).toInt().coerceIn(0, samples.size)
                val end = ceil(window.endSeconds * sampleRate).toInt().coerceIn(start, samples.size)
                val stream = extractor.createStream()
                try {
                    stream.acceptWaveform(samples.copyOfRange(start, end), sampleRate)
                    stream.inputFinished()
                    require(extractor.isReady(stream)) { "Speech is too short for reliable fixed-count clustering." }
                    extractor.compute(stream)
                } finally {
                    stream.release()
                }
            }
            val labels = FixedCountVoiceClusterer.cluster(evidence, speakerCount)
            windows.mapIndexed { index, turn -> turn.copy(speakerId = "speaker-${labels[index] + 1}") }
                .normalizeTurns(samples.size / sampleRate.toFloat())
        } finally {
            extractor.release()
        }
    }

    private fun reconcileIdentities(
        samples: FloatArray,
        sampleRate: Int,
        turns: List<DiarizedAudioTurn>
    ): List<DiarizedAudioTurn> {
        val extractor = SpeakerEmbeddingExtractor(
            context.assets,
            SpeakerEmbeddingExtractorConfig(
                model = "$MODEL_DIR/$EMBEDDING_MODEL",
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                debug = false,
                provider = "cpu"
            )
        )
        return try {
            val embeddings = turns.groupBy { it.speakerId }.mapNotNull { (id, speakerTurns) ->
                // Pool speech across pauses: silence length never decides who is speaking.
                val ranges = speakerTurns.map { turn ->
                    floor(turn.startSeconds * sampleRate).toInt().coerceIn(0, samples.size) until
                        ceil(turn.endSeconds * sampleRate).toInt().coerceIn(0, samples.size)
                }
                val audio = FloatArray(ranges.sumOf { it.count() })
                var offset = 0
                ranges.forEach { range ->
                    samples.copyInto(audio, offset, range.first, range.last + 1)
                    offset += range.count()
                }
                // A short acknowledgement is insufficient evidence for merging identities.
                if (audio.size < sampleRate * 2) return@mapNotNull null
                val stream = extractor.createStream()
                try {
                    stream.acceptWaveform(audio, sampleRate)
                    stream.inputFinished()
                    if (extractor.isReady(stream)) id to extractor.compute(stream) else null
                } finally {
                    stream.release()
                }
            }.toMap()
            SpeakerIdentityResolver.resolve(turns, embeddings)
        } finally {
            extractor.release()
        }
    }

    private fun createDiarizer(expectedSpeakerCount: Int): OfflineSpeakerDiarization {
        val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = "$MODEL_DIR/$SEGMENTATION_MODEL",
                    windowShiftRatio = SEGMENTATION_WINDOW_SHIFT_RATIO
                ),
                numThreads = threadCount,
                debug = false,
                provider = "cpu"
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = "$MODEL_DIR/$EMBEDDING_MODEL",
                numThreads = threadCount,
                debug = false,
                provider = "cpu"
            ),
            clustering = FastClusteringConfig(
                numClusters = expectedSpeakerCount,
                threshold = 0.5f
            ),
            minDurationOn = MIN_SPEECH_SECONDS,
            minDurationOff = MIN_SILENCE_SECONDS
        )
        return OfflineSpeakerDiarization(context.assets, config)
    }

    private fun hasModels(): Boolean {
        val available = context.assets.list(MODEL_DIR)?.toSet().orEmpty()
        return REQUIRED_MODELS.all(available::contains)
    }

    private fun List<DiarizedAudioTurn>.normalizeTurns(
        audioDurationSeconds: Float
    ): List<DiarizedAudioTurn> {
        val normalized = sortedBy(DiarizedAudioTurn::startSeconds)
            .mapNotNull { turn ->
                val start = turn.startSeconds.coerceIn(0f, audioDurationSeconds)
                val end = turn.endSeconds.coerceIn(start, audioDurationSeconds)
                if (end - start < MIN_TURN_SECONDS) null else turn.copy(
                    startSeconds = start,
                    endSeconds = end
                )
            }
        return normalized.fold(mutableListOf()) { merged, turn ->
            val previous = merged.lastOrNull()
            if (
                previous != null &&
                previous.speakerId == turn.speakerId &&
                turn.startSeconds - previous.endSeconds <= MERGE_GAP_SECONDS
            ) {
                merged[merged.lastIndex] = previous.copy(
                    endSeconds = maxOf(previous.endSeconds, turn.endSeconds)
                )
            } else {
                merged += turn
            }
            merged
        }
    }

    private inline fun <T> OfflineSpeakerDiarization.useDiarizer(
        block: (OfflineSpeakerDiarization) -> T
    ): T = try {
        block(this)
    } finally {
        release()
    }

    private companion object {
        const val MODEL_DIR = "models/sherpa-onnx-speaker-diarization"
        const val SEGMENTATION_MODEL = "segmentation-model.onnx"
        const val EMBEDDING_MODEL = "nemo_en_titanet_small.onnx"
        const val MIN_TURN_SECONDS = 0.2f
        // Consultations often have short hand-offs. A 500 ms off-duration smoothed across the
        // 450 ms pauses in our two-voice benchmark and merged doctor/patient speech.
        const val MIN_SPEECH_SECONDS = 0.2f
        const val MIN_SILENCE_SECONDS = 0.25f
        const val MERGE_GAP_SECONDS = 0.15f
        const val SEGMENTATION_WINDOW_SHIFT_RATIO = 0.2f
        val REQUIRED_MODELS = setOf(SEGMENTATION_MODEL, EMBEDDING_MODEL)
    }
}
