package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineMedAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

/** English-only on-device transcription adapted for medical speech. */
class SherpaMedAsrTranscriptionEngine(
    private val context: Context,
    private val diarizationEngine: SpeakerDiarizationEngine? = null
) : AudioTranscriptionEngine {
    override val option: TranscriptionEngineOption = TranscriptionEngineOption.MedAsrEnglish

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage
    ): TranscriptionResult = runCatching {
        require(language == TranscriptionLanguage.English) {
            "Medical English supports English recordings only."
        }
        checkModelAssets()
        val samples = PcmWaveAudio.readMono16Khz(File(audioPath))
        require(samples.isNotEmpty()) { "The recording contains no audio." }
        createRecognizer().useRecognizer { recognizer ->
            val payload = OfflineDiarizedTranscription.transcribe(
                samples = samples,
                sampleRate = PcmWaveAudio.sampleRate,
                diarizationEngine = diarizationEngine,
                recognize = { audio -> recognize(recognizer, audio) }
            )
            require(payload.transcript.isNotBlank()) {
                "No speech was detected. Check the recording and try again."
            }
            payload
        }
    }.fold(
        onSuccess = { TranscriptionResult.Success(it.transcript, it.segments) },
        onFailure = { error ->
            TranscriptionResult.Failure(
                error.message ?: "Medical English transcription could not be completed."
            )
        }
    )

    private fun recognize(recognizer: OfflineRecognizer, samples: FloatArray): String =
        PcmWaveAudio.chunks(samples, MAX_CHUNK_SAMPLES)
            .mapNotNull { chunk ->
                recognizer.createStream().let { stream ->
                    try {
                        stream.acceptWaveform(chunk, PcmWaveAudio.sampleRate)
                        recognizer.decode(stream)
                        recognizer.getResult(stream).text.trim().ifBlank { null }
                    } finally {
                        stream.release()
                    }
                }
            }
            .joinToString(separator = " ")

    private fun createRecognizer(): OfflineRecognizer {
        val modelConfig = OfflineModelConfig(
            medasr = OfflineMedAsrCtcModelConfig(
                model = "$MODEL_DIR/model.int8.onnx"
            ),
            tokens = "$MODEL_DIR/tokens.txt",
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
            provider = "cpu"
        )
        return OfflineRecognizer(
            assetManager = context.assets,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = PcmWaveAudio.sampleRate,
                    featureDim = 80
                ),
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )
        )
    }

    private fun checkModelAssets() {
        val availableFiles = context.assets.list(MODEL_DIR)?.toSet().orEmpty()
        val missing = REQUIRED_MODEL_FILES - availableFiles
        require(missing.isEmpty()) {
            "Medical English model is not installed in this build."
        }
    }

    private inline fun <T> OfflineRecognizer.useRecognizer(
        block: (OfflineRecognizer) -> T
    ): T = try {
        block(this)
    } finally {
        release()
    }

    private companion object {
        const val MAX_CHUNK_SECONDS = 25
        const val MAX_CHUNK_SAMPLES = PcmWaveAudio.sampleRate * MAX_CHUNK_SECONDS
        const val MODEL_DIR = "models/sherpa-onnx-medasr-ctc-en-int8"
        val REQUIRED_MODEL_FILES = setOf("model.int8.onnx", "tokens.txt")
    }
}
