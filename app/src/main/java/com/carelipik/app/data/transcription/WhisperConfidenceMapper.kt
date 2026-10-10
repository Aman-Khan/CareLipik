package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.WhisperDecodingSegment
import com.carelipik.app.domain.transcription.WhisperWordConfidence
import org.json.JSONObject
import kotlin.math.exp

/** Maps native scores only when token text and original character offsets agree exactly. */
internal object WhisperConfidenceMapper {
    fun map(
        rawText: String,
        tokens: List<String>,
        metadataJson: String,
        chunkStartMs: Long,
        chunkEndMs: Long,
        speakerId: String?,
        language: String?
    ): List<WhisperDecodingSegment>? = runCatching {
        val metadata = JSONObject(metadataJson)
        require(metadata.getInt("schema") == 1)
        val scores = metadata.getJSONArray("log_probs")
        val probabilities = (0 until scores.length()).map { scores.optDouble(it, Double.NaN).toFloat() }
        val validScores = probabilities.size == tokens.size && probabilities.all { it.isFinite() && it <= 0f }
        val tokenText = tokens.joinToString("")
        val tokenRanges = buildList {
            var cursor = 0
            tokens.forEach { token ->
                add(cursor until cursor + token.length)
                cursor += token.length
            }
        }
        fun region(rawStart: Int, rawEnd: Int, audioStart: Long, audioEnd: Long): WhisperDecodingSegment {
            val rawRegion = rawText.substring(rawStart, rawEnd)
            val leading = rawRegion.length - rawRegion.trimStart().length
            val text = rawRegion.trim()
            val textStart = rawStart + leading
            val words = if (validScores && tokenText == rawText) {
                Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*").findAll(text).mapNotNull { word ->
                    val first = textStart + word.range.first
                    val last = textStart + word.range.last + 1
                    val selected = tokenRanges.indices.filter {
                        !tokenRanges[it].isEmpty() && tokenRanges[it].first < last && tokenRanges[it].last + 1 > first
                    }.map { probabilities[it] }
                    if (selected.isEmpty()) null else WhisperWordConfidence(
                        word.range.first, word.range.last + 1, word.value,
                        exp(selected.average()).toFloat(), exp(selected.min().toDouble()).toFloat()
                    )
                }.toList()
            } else emptyList()
            val matchingTokens = tokenRanges.indices.filter {
                !tokenRanges[it].isEmpty() && tokenRanges[it].first < rawEnd && tokenRanges[it].last + 1 > rawStart
            }
            return WhisperDecodingSegment(audioStart, audioEnd, text, -1, -1, speakerId, language,
                tokens = if (tokenText == rawText) matchingTokens.map { tokens[it] } else emptyList(),
                tokenLogProbabilities = if (validScores && tokenText == rawText) matchingTokens.map { probabilities[it] } else emptyList(),
                wordConfidences = words, hasNativeConfidence = validScores && tokenText == rawText)
        }
        val texts = metadata.getJSONArray("segment_texts")
        val starts = metadata.getJSONArray("segment_starts")
        val durations = metadata.getJSONArray("segment_durations")
        val sourceTexts = (0 until texts.length()).map { texts.getString(it) }
        val hasSegments = texts.length() > 0 && starts.length() == texts.length() && durations.length() == texts.length() &&
            sourceTexts.joinToString("") == rawText
        val regions = if (hasSegments) runCatching {
            var cursor = 0
            var previousEnd = chunkStartMs
            sourceTexts.mapIndexedNotNull { index, text ->
                val startSeconds = starts.getDouble(index)
                val durationSeconds = durations.getDouble(index)
                require(startSeconds.isFinite() && durationSeconds.isFinite() && startSeconds >= 0 && durationSeconds >= 0)
                val start = chunkStartMs + (startSeconds * 1_000).toLong()
                val end = chunkStartMs + ((startSeconds + durationSeconds) * 1_000).toLong()
                require(start >= previousEnd - 20 && start < chunkEndMs && end <= chunkEndMs + 40 && end > start)
                previousEnd = end
                val from = cursor
                cursor += text.length
                if (text.isBlank()) null else region(from, cursor, start, end.coerceAtMost(chunkEndMs))
            }
        }.getOrElse { listOf(region(0, rawText.length, chunkStartMs, chunkEndMs)) }
        else listOf(region(0, rawText.length, chunkStartMs, chunkEndMs))
        // Timestamp segmentation must not change the primary decoder's wording or spacing.
        if (regions.joinToString(" ") { it.text } != rawText.trim()) {
            listOf(region(0, rawText.length, chunkStartMs, chunkEndMs))
        } else regions.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
