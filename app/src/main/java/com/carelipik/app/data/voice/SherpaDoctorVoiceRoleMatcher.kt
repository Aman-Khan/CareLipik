package com.carelipik.app.data.voice

import android.content.Context
import com.carelipik.app.data.transcription.PcmWaveAudio
import com.carelipik.app.domain.transcription.DiarizedAudioTurn
import com.carelipik.app.domain.voice.DoctorVoiceMatchPolicy
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatcher
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Computes enrollment and consultation embeddings locally with the bundled Sherpa model. */
class SherpaDoctorVoiceRoleMatcher(
    context: Context,
    enrollmentFileOverride: File? = null
) : DoctorVoiceRoleMatcher {
    private val applicationContext = context.applicationContext
    private val enrollmentFileOverride = enrollmentFileOverride
    private val enrollmentDirectory = File(applicationContext.filesDir, "doctor_voice")

    override fun match(
        samples: FloatArray,
        sampleRate: Int,
        turns: List<DiarizedAudioTurn>
    ): DoctorVoiceRoleMatchResult {
        val enrollmentFiles = enrollmentFiles()
        if (enrollmentFiles.isEmpty()) return DoctorVoiceRoleMatchResult.NotEnrolled
        if (!hasModel()) {
            return DoctorVoiceRoleMatchResult.Unavailable(
                "Offline doctor voice matching model is not installed in this build."
            )
        }
        return runCatching {
            require(sampleRate == PcmWaveAudio.sampleRate) {
                "Doctor voice matching requires ${PcmWaveAudio.sampleRate} Hz audio."
            }
            createExtractor().useExtractor { extractor ->
                val referenceEmbedding = enrollmentFiles
                    .map { file -> extractor.embedding(PcmWaveAudio.readMono16Khz(file), sampleRate) }
                    .averageEmbedding()
                val candidates = turns.map(DiarizedAudioTurn::speakerId).distinct().mapNotNull {
                    speakerId ->
                    concatenateSpeakerAudio(samples, sampleRate, turns, speakerId)
                        .takeIf { it.size >= MIN_SPEAKER_SAMPLES }
                        ?.let { speakerAudio ->
                            speakerId to cosineSimilarity(
                                referenceEmbedding,
                                extractor.embedding(speakerAudio, sampleRate)
                            )
                        }
                }.toMap()
                DoctorVoiceMatchPolicy.choose(candidates)
            }
        }.getOrElse { error ->
            DoctorVoiceRoleMatchResult.Unavailable(
                error.message ?: "Offline doctor voice matching could not be completed."
            )
        }
    }

    private fun enrollmentFiles(): List<File> {
        enrollmentFileOverride?.let { return listOfNotNull(it.takeIf(File::isFile)) }
        val multiple = enrollmentDirectory.listFiles().orEmpty()
            .filter { it.isFile && ENROLLMENT_FILE_PATTERN.matches(it.name) }
            .sortedBy { it.name }
        if (multiple.isNotEmpty()) return multiple
        return listOfNotNull(
            File(enrollmentDirectory, "doctor-voice-sample.wav").takeIf(File::isFile)
        )
    }

    private fun List<FloatArray>.averageEmbedding(): FloatArray {
        require(isNotEmpty()) { "No doctor voice embeddings were available." }
        val average = FloatArray(first().size)
        forEach { embedding ->
            require(embedding.size == average.size) { "Inconsistent voice embedding size." }
            embedding.indices.forEach { index -> average[index] += embedding[index] }
        }
        average.indices.forEach { index -> average[index] /= size }
        val norm = sqrt(average.sumOf { value -> (value * value).toDouble() }).toFloat()
        require(norm > 0f) { "Invalid averaged doctor voice embedding." }
        average.indices.forEach { index -> average[index] /= norm }
        return average
    }

    private fun concatenateSpeakerAudio(
        samples: FloatArray,
        sampleRate: Int,
        turns: List<DiarizedAudioTurn>,
        speakerId: String
    ): FloatArray {
        val cleanTurns = turns.filter { turn ->
            turns.none { other ->
                other.speakerId != turn.speakerId &&
                    minOf(turn.endSeconds, other.endSeconds) -
                    maxOf(turn.startSeconds, other.startSeconds) > OVERLAP_TOLERANCE_SECONDS
            }
        }
        val ranges = cleanTurns.filter { it.speakerId == speakerId }.mapNotNull { turn ->
            val start = floor(turn.startSeconds * sampleRate).toInt().coerceIn(0, samples.size)
            val end = ceil(turn.endSeconds * sampleRate).toInt().coerceIn(start, samples.size)
            (start until end).takeIf { !it.isEmpty() }
        }
        val output = FloatArray(ranges.sumOf(IntRange::count))
        var offset = 0
        ranges.forEach { range ->
            samples.copyInto(output, offset, range.first, range.last + 1)
            offset += range.count()
        }
        return output
    }

    private fun createExtractor() = SpeakerEmbeddingExtractor(
        applicationContext.assets,
        SpeakerEmbeddingExtractorConfig(
            model = "$MODEL_DIR/$EMBEDDING_MODEL",
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
            debug = false,
            provider = "cpu"
        )
    )

    private fun SpeakerEmbeddingExtractor.embedding(audio: FloatArray, sampleRate: Int): FloatArray {
        val stream = createStream()
        return try {
            stream.acceptWaveform(audio, sampleRate)
            stream.inputFinished()
            require(isReady(stream)) { "The voice clip is too short to compare reliably." }
            compute(stream)
        } finally {
            stream.release()
        }
    }

    private fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        require(first.size == second.size && first.isNotEmpty()) { "Invalid voice embedding." }
        var dot = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0
        first.indices.forEach { index ->
            dot += first[index] * second[index]
            firstNorm += first[index] * first[index]
            secondNorm += second[index] * second[index]
        }
        val denominator = sqrt(firstNorm) * sqrt(secondNorm)
        return if (denominator == 0.0) -1f else (dot / denominator).toFloat()
    }

    private fun hasModel(): Boolean = applicationContext.assets.list(MODEL_DIR)
        ?.contains(EMBEDDING_MODEL) == true

    private inline fun <T> SpeakerEmbeddingExtractor.useExtractor(
        block: (SpeakerEmbeddingExtractor) -> T
    ): T = try {
        block(this)
    } finally {
        release()
    }

    private companion object {
        const val MODEL_DIR = "models/sherpa-onnx-speaker-diarization"
        // Keep this aligned with SherpaOfflineSpeakerDiarizationEngine: the same TitaNet
        // embedding space must be used for enrollment and consultation speaker turns.
        const val EMBEDDING_MODEL = "nemo_en_titanet_small.onnx"
        const val MIN_SPEAKER_SAMPLES = PcmWaveAudio.sampleRate
        const val OVERLAP_TOLERANCE_SECONDS = 0.05f
        val ENROLLMENT_FILE_PATTERN = Regex("doctor-voice-sample-[1-3]\\.wav")
    }
}
