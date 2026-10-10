package com.carelipik.app.domain.transcription

import kotlin.math.sqrt

/** Reconciles fragmented speaker labels using voice evidence from the entire recording. */
object SpeakerIdentityResolver {
    private const val MIN_SIMILARITY = 0.70f

    fun resolve(
        turns: List<DiarizedAudioTurn>,
        embeddings: Map<String, FloatArray>
    ): List<DiarizedAudioTurn> {
        val groups = turns.sortedBy { it.startSeconds }.map { it.speakerId }.distinct()
            .map { mutableListOf(it) }.toMutableList()
        // Complete-link merging prevents a chain of intermediate voices joining distinct people.
        while (true) {
            var bestPair: Pair<Int, Int>? = null
            var bestScore = MIN_SIMILARITY
            for (first in groups.indices) {
                for (second in first + 1 until groups.size) {
                    val score = groups[first].minOf { left ->
                        groups[second].minOf { right ->
                            similarity(embeddings[left], embeddings[right])
                        }
                    }
                    if (score >= bestScore) {
                        bestScore = score
                        bestPair = first to second
                    }
                }
            }
            val (first, second) = bestPair ?: break
            groups[first].addAll(groups.removeAt(second))
        }
        val labels = groups.flatMapIndexed { index, group ->
            group.map { it to "speaker-${index + 1}" }
        }.toMap()
        return turns.map { it.copy(speakerId = labels.getValue(it.speakerId)) }
    }

    private fun similarity(first: FloatArray?, second: FloatArray?): Float {
        if (first == null || second == null || first.isEmpty() || first.size != second.size) {
            return -1f
        }
        if (first.any { !it.isFinite() } || second.any { !it.isFinite() }) return -1f
        var dot = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0
        first.indices.forEach { index ->
            dot += first[index].toDouble() * second[index]
            firstNorm += first[index].toDouble() * first[index]
            secondNorm += second[index].toDouble() * second[index]
        }
        val norm = sqrt(firstNorm * secondNorm)
        return if (norm > 0.0) (dot / norm).toFloat() else -1f
    }
}
