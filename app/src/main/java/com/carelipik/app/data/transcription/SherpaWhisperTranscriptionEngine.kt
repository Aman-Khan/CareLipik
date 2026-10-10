package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
import com.carelipik.app.domain.transcription.WhisperDecodingSegment
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatcher
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import kotlinx.coroutines.CancellationException

internal data class WhisperPrimaryResult(
    val result: TranscriptionResult.Success,
    val decodingSegments: List<WhisperDecodingSegment>
)

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
        val samples = PcmWaveAudio.readMono16Khz(File(audioPath))
        transcribeSamples(samples, language).result
    }.fold(
        onSuccess = { it },
        onFailure = { error ->
            if (error is CancellationException) throw error
            TranscriptionResult.Failure(
                error.message ?: "Offline transcription could not be completed."
            )
        }
    )

    internal fun transcribeSamples(
        samples: FloatArray,
        language: TranscriptionLanguage,
        checkCancelled: () -> Unit = {},
        fallbackAfterEmptyDiarization: Boolean = true,
        collectConfidence: Boolean = false
    ): WhisperPrimaryResult {
        checkCancelled()
        checkModelAssets()
        require(samples.isNotEmpty()) { "The recording contains no audio." }
        val decoded = mutableListOf<WhisperDecodingSegment>()
        val payload = createRecognizer(language, collectConfidence).useRecognizer { recognizer ->
            val payload = OfflineDiarizedTranscription.transcribe(
                samples = samples,
                sampleRate = PcmWaveAudio.sampleRate,
                diarizationEngine = diarizationEngine,
                doctorVoiceRoleMatcher = doctorVoiceRoleMatcher,
                fallbackAfterEmptyDiarization = fallbackAfterEmptyDiarization,
                recognizeWithTiming = { audio, start, speaker ->
                    recognize(recognizer, audio, start, speaker, decoded, checkCancelled, collectConfidence)
                },
                recognize = { audio ->
                    recognize(recognizer, audio, 0.0, null, decoded, checkCancelled, collectConfidence)
                }
            )
            require(payload.transcript.isNotBlank()) {
                "No speech was detected. Check the recording and try again."
            }
            payload
        }
        checkCancelled()
        // Construct exact character offsets from the same labels/spaces used by the shared pipeline.
        // If that contract ever changes, retain Whisper and omit untrustworthy edit offsets.
        val text = StringBuilder()
        val mapped = mutableListOf<WhisperDecodingSegment>()
        decoded.forEachIndexed { index, segment ->
            val previous = decoded.getOrNull(index - 1)
            if (index == 0 || previous?.speakerId != segment.speakerId) {
                if (index > 0) text.append("\n\n")
                segment.speakerId?.let { id ->
                    text.append(id.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) })
                        .append(": ")
                }
            } else {
                text.append(' ')
            }
            val start = text.length
            text.append(segment.text)
            mapped += segment.copy(transcriptStartIndex = start, transcriptEndIndex = text.length)
        }
        return WhisperPrimaryResult(
            result = TranscriptionResult.Success(payload.transcript, payload.segments, payload.doctorVoiceMatch,
                speakerSeparationWarning = payload.speakerSeparationWarning),
            decodingSegments = if (text.toString() == payload.transcript) mapped else decoded.toList()
        )
    }

    private fun recognize(
        recognizer: OfflineRecognizer,
        samples: FloatArray,
        startSeconds: Double,
        speakerId: String?,
        decoded: MutableList<WhisperDecodingSegment>,
        checkCancelled: () -> Unit,
        collectConfidence: Boolean
    ): String {
        var offsetSamples = 0
        return PcmWaveAudio.chunks(samples, MAX_CHUNK_SAMPLES)
            .mapNotNull { chunk ->
                checkCancelled()
                val startMs = ((startSeconds + offsetSamples.toDouble() / PcmWaveAudio.sampleRate) * 1_000).toLong()
                offsetSamples += chunk.size
                val endMs = ((startSeconds + offsetSamples.toDouble() / PcmWaveAudio.sampleRate) * 1_000).toLong()
                recognizer.createStream().let { stream ->
                    try {
                        if (collectConfidence) stream.setOption("carelipik.whisper.collect_confidence", "1")
                        stream.acceptWaveform(chunk, PcmWaveAudio.sampleRate)
                        recognizer.decode(stream)
                        checkCancelled()
                        val result = recognizer.getResult(stream)
                        val text = result.text.trim()
                        if (text.isBlank()) null else {
                            val confidenceRegions = if (collectConfidence) WhisperConfidenceMapper.map(
                                result.text, result.tokens.toList(), stream.getOption("carelipik.whisper.confidence.v1"),
                                startMs, endMs, speakerId, result.lang.takeIf(String::isNotBlank)
                            ) else null
                            if (confidenceRegions != null) {
                                decoded += confidenceRegions
                                return@let confidenceRegions.joinToString(" ") { it.text }
                            }
                            decoded += WhisperDecodingSegment(
                                startMs = startMs,
                                endMs = endMs,
                                text = text,
                                transcriptStartIndex = -1,
                                transcriptEndIndex = -1,
                                speakerId = speakerId,
                                language = result.lang.takeIf(String::isNotBlank),
                                tokens = result.tokens.toList(),
                                tokenStartTimesMs = result.timestamps.filter { it.isFinite() }
                                    .map { startMs + (it * 1_000).toLong() },
                                tokenDurationsMs = result.durations.filter { it.isFinite() }
                                    .map { (it * 1_000).toLong() },
                                rawTokenTimestampsSeconds = result.timestamps.toList(),
                                rawTokenDurationsSeconds = result.durations.toList(),
                                emotion = result.emotion.takeIf(String::isNotBlank),
                                event = result.event.takeIf(String::isNotBlank)
                            )
                            text
                        }
                    } finally {
                        stream.release()
                    }
                }
            }
            .joinToString(separator = " ")
    }

    private fun createRecognizer(language: TranscriptionLanguage, collectConfidence: Boolean): OfflineRecognizer {
        val modelConfig = OfflineModelConfig(
            whisper = OfflineWhisperModelConfig(
                encoder = "$MODEL_DIR/small-encoder.int8.onnx",
                decoder = "$MODEL_DIR/small-decoder.int8.onnx",
                language = language.whisperCode,
                task = "transcribe",
                tailPaddings = -1,
                enableSegmentTimestamps = collectConfidence
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
        const val MAX_CHUNK_SECONDS = 25
        const val MAX_CHUNK_SAMPLES = PcmWaveAudio.sampleRate * MAX_CHUNK_SECONDS
        const val MODEL_DIR = "models/sherpa-onnx-whisper-small"
        val REQUIRED_MODEL_FILES = setOf(
            "small-encoder.int8.onnx",
            "small-decoder.int8.onnx",
            "small-tokens.txt"
        )
    }
}
