package com.carelipik.app.domain.transcription

import java.util.zip.Deflater

fun interface UncertaintyDetector {
    fun detect(segments: List<WhisperDecodingSegment>): List<UncertainSegment>
}

/** Actual decoder scores plus observable text/timing heuristics; no invented probabilities. */
class ConservativeWhisperUncertaintyDetector(
    private val config: HybridTranscriptionConfig = HybridTranscriptionConfig()
) : UncertaintyDetector {
    override fun detect(segments: List<WhisperDecodingSegment>): List<UncertainSegment> =
        segments.mapIndexedNotNull { index, segment ->
            val words = Regex("[\\p{L}\\p{N}']+").findAll(segment.text.lowercase())
                .map { it.value }.toList()
            val uncertainWords = segment.wordConfidences.filter {
                it.probability < config.wordProbabilityThreshold ||
                    it.minimumTokenProbability < config.minimumTokenProbabilityThreshold
            }
            val reasons = buildList {
                if (uncertainWords.isNotEmpty()) add("Whisper decoder assigned low probability to words in this region")
                if (hasRepetition(words)) add("Whisper repeats the same word sequence")
                if (segment.text.length >= 100 && compressionRatio(segment.text) >= config.compressionRatioThreshold) {
                    add("Whisper text is unusually repetitive/compressible")
                }
                if (words.size <= 2 && segment.endMs - segment.startMs >= config.sparseSpeechSeconds * 1_000) {
                    add("Very little decoded text for this audio interval")
                }
                val timestamps = segment.rawTokenTimestampsSeconds
                val durationSeconds = (segment.endMs - segment.startMs) / 1_000f
                if (timestamps.any { !it.isFinite() || it < 0f || it > durationSeconds + 0.5f } ||
                    timestamps.zipWithNext().any { (first, second) -> second < first } ||
                    (timestamps.isNotEmpty() && timestamps.size != segment.tokens.size)
                ) {
                    add("Whisper token timestamps are inconsistent")
                }
            }
            if (reasons.isEmpty()) null else UncertainSegment(index, segment.startMs, segment.endMs, reasons,
                uncertaintyScore = uncertainWords.minOfOrNull { it.probability }?.let { 1f - it },
                uncertainWords = uncertainWords)
        }

    private fun hasRepetition(words: List<String>): Boolean {
        for (width in 1..4) {
            for (start in 0..(words.size - width * config.repetitionCount)) {
                val phrase = words.subList(start, start + width)
                if ((1 until config.repetitionCount).all { repetition ->
                        words.subList(start + width * repetition, start + width * (repetition + 1)) == phrase
                    }) return true
            }
        }
        return false
    }

    private fun compressionRatio(text: String): Float {
        val input = text.toByteArray(Charsets.UTF_8)
        val deflater = Deflater()
        return try {
            deflater.setInput(input)
            deflater.finish()
            val buffer = ByteArray(input.size + 64)
            val compressed = deflater.deflate(buffer)
            if (compressed == 0) 1f else input.size.toFloat() / compressed
        } finally {
            deflater.end()
        }
    }
}

/** Romanized Hindi is not assumed to be English simply because it uses Latin letters. */
object MedAsrLanguageCompatibility {
    fun isEnglish(segment: WhisperDecodingSegment, selectedLanguage: TranscriptionLanguage): Boolean {
        if (segment.text.none(Char::isLetter)) return false
        if (segment.text.any { it.isLetter() && Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN }) {
            return false
        }
        val detected = segment.language?.lowercase()?.trim().orEmpty()
        return if (detected.isNotEmpty()) {
            detected == "en" || detected == "english" || detected.startsWith("en-")
        } else {
            selectedLanguage == TranscriptionLanguage.English
        }
    }
}
