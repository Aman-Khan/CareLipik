package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.domain.transcription.DiarizedAudioTurn
import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
import com.carelipik.app.domain.transcription.SpeakerDiarizationResult
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig

/** Fully on-device two-speaker diarization using Sherpa-ONNX. */
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
            createDiarizer(expectedSpeakerCount).useDiarizer { diarizer ->
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
