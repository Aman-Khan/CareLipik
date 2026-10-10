package com.carelipik.app.domain.transcription

/** Suggest-only word alignment. Clinical text, including numbers and negations, is never auto-applied. */
class HybridTranscriptMerger(private val config: HybridTranscriptionConfig = HybridTranscriptionConfig()) {
    fun compare(
        id: String,
        startMs: Long,
        endMs: Long,
        whisperText: String,
        whisperStartIndex: Int,
        medAsrText: String,
        reasons: List<String>
    ): List<HybridCorrection> {
        val original = words(whisperText)
        val alternative = words(medAsrText)
        if (original.map { it.normalized } == alternative.map { it.normalized }) return emptyList()
        fun unresolved() = listOf(HybridCorrection(
            id, startMs, endMs, whisperText, medAsrText, null, null,
            whisperText, medAsrText, reasons + "Phrase alignment was not reliable; edit manually or keep Whisper",
            CorrectionStatus.Unresolved
        ))
        if (original.isEmpty() || alternative.isEmpty() || original.size > 256 || alternative.size > 256) {
            return unresolved()
        }
        val matrix = Array(original.size + 1) { IntArray(alternative.size + 1) }
        for (i in original.indices.reversed()) {
            for (j in alternative.indices.reversed()) {
                matrix[i][j] = if (original[i].normalized == alternative[j].normalized) {
                    matrix[i + 1][j + 1] + 1
                } else maxOf(matrix[i + 1][j], matrix[i][j + 1])
            }
        }
        val anchors = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < original.size && j < alternative.size) {
            if (original[i].normalized == alternative[j].normalized) {
                anchors += i++ to j++
            } else if (matrix[i + 1][j] >= matrix[i][j + 1]) i++ else j++
        }
        if (anchors.size < config.minimumMatchingWords ||
            anchors.size.toFloat() / original.size < config.minimumAlignmentFraction ||
            anchors.first().first != 0 || anchors.last().first != original.lastIndex
        ) return unresolved()

        // Padded context outside the first/last original anchor is deliberately not inserted.
        val corrections = mutableListOf<HybridCorrection>()
        anchors.zipWithNext().forEach { (left, right) ->
            val oldStart = left.first + 1
            val oldEnd = right.first
            val newStart = left.second + 1
            val newEnd = right.second
            if (oldStart == oldEnd && newStart == newEnd) return@forEach
            if ((oldEnd - oldStart) + (newEnd - newStart) > config.maximumChangedWords) return unresolved()
            val start = if (oldStart < oldEnd) original[oldStart].start else original[oldEnd].start
            val end = if (oldStart < oldEnd) original[oldEnd - 1].end else start
            val replacement = if (newStart < newEnd) {
                medAsrText.substring(alternative[newStart].start, alternative[newEnd - 1].end) +
                    if (start == end) " " else ""
            } else ""
            corrections += HybridCorrection(
                id = "$id:${corrections.size}",
                startMs = startMs,
                endMs = endMs,
                originalText = whisperText.substring(start, end),
                suggestedText = replacement,
                transcriptStartIndex = whisperStartIndex + start,
                transcriptEndIndex = whisperStartIndex + end,
                whisperContext = whisperText,
                medAsrAlternative = medAsrText,
                reasons = reasons,
                status = CorrectionStatus.Suggested
            )
        }
        // Edge-only disagreements may be padded context, or missed speech. Keep the full
        // alternative visible instead of silently discarding a difference we cannot localize.
        return if (corrections.isEmpty()) unresolved() else corrections
    }

    private data class Word(val normalized: String, val start: Int, val end: Int)

    private fun words(text: String): List<Word> = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
        .findAll(text).map { Word(it.value.lowercase().replace('’', '\''), it.range.first, it.range.last + 1) }.toList()
}
