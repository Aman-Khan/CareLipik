package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatcher
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/** On-device multilingual Whisper transcription. No audio or text leaves the phone. */
class SherpaWhisperTranscriptionEngine(
    private val context: Context,
    private val diarizationEngine: SpeakerDiarizationEngine? = null,
    private val doctorVoiceRoleMatcher: DoctorVoiceRoleMatcher? = null
) : AudioTranscriptionEngine {
    override val option: TranscriptionEngineOption = TranscriptionEngineOption.WhisperMultilingual

    override fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage
    ): TranscriptionResult = runCatching {
        checkModelAssets()
        val samples = PcmWaveAudio.readMono16Khz(File(audioPath))
        require(samples.isNotEmpty()) { "The recording contains no audio." }
        createRecognizer(language).useRecognizer { recognizer ->
            val payload = OfflineDiarizedTranscription.transcribe(
                samples = samples,
                sampleRate = PcmWaveAudio.sampleRate,
                diarizationEngine = diarizationEngine,
                doctorVoiceRoleMatcher = doctorVoiceRoleMatcher,
                recognize = { audio -> recognize(recognizer, audio) }
            )
            require(payload.transcript.isNotBlank()) {
                "No speech was detected. Check the recording and try again."
            }
            payload
        }
    }.fold(
        onSuccess = {
            TranscriptionResult.Success(
                it.transcript,
                it.segments,
                it.doctorVoiceMatch,
                it.speakerSeparationWarning
            )
        },
        onFailure = { error ->
            TranscriptionResult.Failure(
                error.message ?: "Offline transcription could not be completed."
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

    private fun createRecognizer(language: TranscriptionLanguage): OfflineRecognizer {
        val modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = "$MODEL_DIR/small-encoder.int8.onnx",
                decoder = "$MODEL_DIR/small-decoder.int8.onnx",
                language = language.whisperCode,
                task = "transcribe",
                tailPaddings = -1
            ),
            tokens = "$MODEL_DIR/small-tokens.txt",
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
            provider = "cpu",
            modelType = "whisper"
        )
        return OfflineRecognizer(
            assetManager = context.assets,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = PcmWaveAudio.sampleRate, featureDim = 80),
                modelConfig = modelConfig,
                decodingMethod = "greedy_search"
            )
        )
    }

    private fun checkModelAssets() {
        val availableFiles = context.assets.list(MODEL_DIR)?.toSet().orEmpty()
        val missing = REQUIRED_MODEL_FILES - availableFiles
        require(missing.isEmpty()) {
            "Offline transcription model is not installed in this build."
        }
    }

    private inline fun <T> OfflineRecognizer.useRecognizer(block: (OfflineRecognizer) -> T): T {
        return try {
            block(this)
        } finally {
            release()
        }
    }

    private companion object {
        const val MAX_CHUNK_SECONDS = 20
        const val MAX_CHUNK_SAMPLES = PcmWaveAudio.sampleRate * MAX_CHUNK_SECONDS
        const val MODEL_DIR = "models/sherpa-onnx-whisper-small"
        val REQUIRED_MODEL_FILES = setOf(
            "small-encoder.int8.onnx",
            "small-decoder.int8.onnx",
            "small-tokens.txt"
        )
    }
}
