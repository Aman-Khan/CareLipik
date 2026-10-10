package com.carelipik.app.data.transcription

import android.content.Context
import android.os.Build
import com.carelipik.app.domain.transcription.SpeakerDiarizationEngine
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.WhisperDecodingSegment
import com.carelipik.app.domain.transcription.WhisperWordConfidence
import com.carelipik.app.domain.transcription.WhisperWordTiming
import com.carelipik.app.domain.transcription.SpeakerDiarizationResult
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatcher
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.ln
import org.json.JSONObject

/** Optional GPU primary engine for LLM-Guided Hybrid only. Exceptions trigger Sherpa fallback. */
internal class VulkanWhisperTranscriber(private val context: Context,
    private val diarizationEngine: SpeakerDiarizationEngine?,
    private val doctorVoiceRoleMatcher: DoctorVoiceRoleMatcher?) {
    fun transcribe(samples: FloatArray, language: TranscriptionLanguage,
        checkCancelled: () -> Unit, onDetail: (String) -> Unit, useGpu: Boolean = true): WhisperPrimaryResult {
        val backend = if (useGpu) "Whisper Vulkan" else "Whisper native CPU"
        require(Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a") { "Whisper Vulkan requires arm64-v8a" }
        checkCancelled()
        onDetail("$backend: preparing verified Small Q8_0 model")
        val model = prepareModel(checkCancelled)
        onDetail("$backend language: ${language.displayName} (${language.whisperCode.ifEmpty { "auto" }})")
        val decoded = mutableListOf<WhisperDecodingSegment>()
        RemoteWhisperVulkanSession(context, checkCancelled, onDetail).use { worker ->
            worker.open(model.absolutePath, useGpu)
            var offset = 0
            for (chunk in chunks(samples, checkCancelled)) {
                checkCancelled()
                val startMs = offset.toLong() * 1000 / PcmWaveAudio.sampleRate
                onDetail("$backend: transcribing chronological audio at ${startMs / 1000}s")
                val response = worker.recognize(chunk, language.whisperCode).toString(Charsets.UTF_8)
                val regions = map(JSONObject(response), startMs,
                    startMs + chunk.size.toLong() * 1000 / PcmWaveAudio.sampleRate, null)
                regions.forEach { WhisperOutputValidation.requireReadable(it.text, language) }
                onDetail("$backend decoded ${regions.size} phrases; ${regions.count { it.wordTimings.isNotEmpty() }} have validated word timestamps")
                decoded += regions
                offset += chunk.size
            }
        }
        require(decoded.isNotEmpty()) { "Whisper Vulkan returned no speech; retrying Sherpa CPU" }
        onDetail("$backend released; running independent speaker diarization")
        checkCancelled()
        val diarization = diarizationEngine?.diarize(samples, PcmWaveAudio.sampleRate, -1)
        checkCancelled()
        val doctorMatch = (diarization as? SpeakerDiarizationResult.Success)?.let {
            doctorVoiceRoleMatcher?.match(samples, PcmWaveAudio.sampleRate, it.turns)
        }
        checkCancelled()
        onDetail("Whisper primary backend: ${if (useGpu) "Vulkan GPU" else "native CPU"}; released before Qwen")
        return PostTranscriptionSpeakerAlignment.assemble(decoded, diarization, doctorMatch,
            "Whisper primary backend: ${if (useGpu) "Vulkan GPU" else "native CPU with word timestamps"} (multilingual Small Q8_0).")
    }

    private fun chunks(samples: FloatArray, checkCancelled: () -> Unit): Sequence<FloatArray> = sequence {
        val rate = PcmWaveAudio.sampleRate
        val frame = rate / 25
        var start = 0
        while (start < samples.size) {
            checkCancelled()
            val maximum = (start + rate * 25).coerceAtMost(samples.size)
            var end = maximum
            if (maximum < samples.size) {
                var quietest = 0.003 * 0.003
                for (position in start + rate * 20 until maximum - frame step frame) {
                    var energy = 0.0
                    for (i in position until position + frame) energy += samples[i].toDouble() * samples[i]
                    energy /= frame
                    if (energy < quietest) { quietest = energy; end = position + frame / 2 }
                }
            }
            yield(samples.copyOfRange(start, end))
            start = end
        }
    }

    private fun map(root: JSONObject, chunkStart: Long, chunkEnd: Long, speaker: String?): List<WhisperDecodingSegment> {
        val segments = root.getJSONArray("segments")
        return (0 until segments.length()).mapNotNull { index ->
            val source = segments.getJSONObject(index)
            val raw = source.getString("text")
            val text = raw.trim()
            if (text.isBlank()) return@mapNotNull null
            val relativeStart = source.getLong("start_ms")
            val relativeEnd = source.getLong("end_ms")
            require(relativeStart >= 0 && relativeEnd >= relativeStart && relativeEnd <= 30_000) {
                "Invalid Whisper Vulkan timestamps"
            }
            // Whisper decodes a padded 30-second window. Clip its final boundary to
            // the supplied audio; padding must never become a verification region.
            val start = chunkStart + relativeStart
            if (start >= chunkEnd) return@mapNotNull null
            val end = (chunkStart + relativeEnd).coerceAtMost(chunkEnd)
            val tokens = source.getJSONArray("tokens")
            val bytes = ByteArrayOutputStream()
            val ranges = mutableListOf<IntRange>()
            val scores = mutableListOf<Float>()
            val starts = mutableListOf<Long>()
            val durations = mutableListOf<Long>()
            val alignments = mutableListOf<Long?>()
            for (i in 0 until tokens.length()) {
                val token = tokens.getJSONObject(i)
                val hex = token.getString("bytes")
                require(hex.length % 2 == 0)
                val piece = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val from = bytes.size()
                bytes.write(piece)
                ranges += from until bytes.size()
                val probability = token.getDouble("p").toFloat()
                require(probability.isFinite() && probability in 0f..1f)
                scores += probability
                starts += (chunkStart + token.getLong("start_ms")).coerceIn(chunkStart, chunkEnd)
                durations += (token.getLong("end_ms") - token.getLong("start_ms")).coerceAtLeast(0)
                val alignment = token.optLong("alignment_ms", -1)
                alignments += if (alignment >= 0 && chunkStart + alignment in start..end) chunkStart + alignment else null
            }
            // UTF-8 token fragments are joined before decoding, never independently corrupted.
            val tokenText = bytes.toByteArray().toString(Charsets.UTF_8)
            val leading = raw.length - raw.trimStart().length
            val aligned = tokenText == raw
            val wordTimings = if (aligned) Regex("\\S+").findAll(text).mapNotNull { word ->
                val first = raw.substring(0, leading + word.range.first).toByteArray(Charsets.UTF_8).size
                val last = raw.substring(0, leading + word.range.last + 1).toByteArray(Charsets.UTF_8).size
                val selected = ranges.indices.filter { !ranges[it].isEmpty() && ranges[it].first < last && ranges[it].last + 1 > first &&
                    durations[it] > 0 && starts[it] < end && starts[it] + durations[it] > start }
                if (selected.isEmpty()) null else {
                    val from = selected.minOf { starts[it] }.coerceAtLeast(start)
                    val to = selected.maxOf { starts[it] + durations[it] }.coerceAtMost(end)
                    val points = selected.mapNotNull { alignments[it] }.sorted()
                    val alignment = points.getOrNull(points.size / 2)
                    if (to <= from) null else WhisperWordTiming(word.range.first, word.range.last + 1, from, to, alignment)
                }
            }.toList() else emptyList()
            val validWordTimings = wordTimings.takeIf { it.size == Regex("\\S+").findAll(text).count() &&
                it.zipWithNext().all { (left, right) -> right.startMs >= left.startMs && right.endMs >= left.endMs &&
                    (left.alignmentMs == null || right.alignmentMs == null || right.alignmentMs >= left.alignmentMs) } }.orEmpty()
            val confidences = if (aligned) Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
                .findAll(text).mapNotNull { word ->
                    val first = raw.substring(0, leading + word.range.first).toByteArray(Charsets.UTF_8).size
                    val last = raw.substring(0, leading + word.range.last + 1).toByteArray(Charsets.UTF_8).size
                    val selected = ranges.indices.filter { !ranges[it].isEmpty() && ranges[it].first < last && ranges[it].last + 1 > first }
                        .map { scores[it].coerceAtLeast(0.000001f) }
                    if (selected.isEmpty()) null else WhisperWordConfidence(word.range.first, word.range.last + 1,
                        word.value, exp(selected.map { ln(it.toDouble()) }.average()).toFloat(), selected.min())
                }.toList() else emptyList()
            WhisperDecodingSegment(start, end.coerceAtMost(chunkEnd), text, -1, -1, speaker,
                root.optString("language").takeIf { it.isNotBlank() },
                tokenStartTimesMs = starts, tokenDurationsMs = durations,
                tokenLogProbabilities = if (aligned) scores.map { ln(it.coerceAtLeast(0.000001f)) } else emptyList(),
                wordConfidences = confidences, hasNativeConfidence = aligned && confidences.isNotEmpty(),
                wordTimings = validWordTimings)
        }
    }

    private fun prepareModel(checkCancelled: () -> Unit): File {
        val directory = File(context.noBackupFilesDir, "models/whisper-vulkan").apply { mkdirs() }
        val model = File(directory, FILE_NAME)
        val stamp = File(directory, "$FILE_NAME.verified")
        fun signature() = "$MODEL_SHA256:${model.length()}:${model.lastModified()}"
        if (model.length() == MODEL_BYTES && stamp.isFile && stamp.readText() == signature()) return model
        fun valid(file: File): Boolean {
            if (!file.isFile || file.length() != MODEL_BYTES) return false
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    checkCancelled()
                    val size = input.read(buffer)
                    if (size < 0) break
                    hash.update(buffer, 0, size)
                }
            }
            return hash.digest().joinToString("") { "%02x".format(it) } == MODEL_SHA256
        }
        if (!valid(model)) {
            val pending = File(directory, "$FILE_NAME.pending")
            try {
                context.assets.open("models/whisper-vulkan/$FILE_NAME").use { input ->
                    pending.outputStream().buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            checkCancelled()
                            val size = input.read(buffer)
                            if (size < 0) break
                            output.write(buffer, 0, size)
                        }
                    }
                }
                require(valid(pending)) { "Whisper Vulkan model checksum mismatch" }
                require(pending.renameTo(model)) { "Could not install Whisper Vulkan model" }
            } finally { pending.delete() }
        }
        stamp.writeText(signature())
        return model
    }
    private companion object {
        const val FILE_NAME = "ggml-small-q8_0.bin"
        const val MODEL_BYTES = 264_464_607L
        const val MODEL_SHA256 = "49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f"
    }
}
