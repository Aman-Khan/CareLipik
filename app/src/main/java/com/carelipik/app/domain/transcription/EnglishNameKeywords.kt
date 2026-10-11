package com.carelipik.app.domain.transcription

import java.text.Normalizer
import java.util.Locale

/** SentencePiece BPE merges for the English model's uppercase ASCII name vocabulary. */
class EnglishNameKeywords(private val scores: Map<String, Float>) {
    fun encode(name: String): String {
        val normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFKC).uppercase(Locale.ROOT)
        require(normalized.matches(Regex("[A-Z]+(?:[ '-][A-Z]+)*"))) {
            "Enter the patient name using English letters."
        }
        val words = normalized.split(Regex("[ '-]+")).distinct()
        require(words.all { it.length >= 2 }) { "Use full names rather than initials for masking." }
        return words.mapIndexed { index, word ->
            val pieces = ("▁" + word).map { it.toString() }.toMutableList()
            while (pieces.size > 1) {
                var bestIndex = -1
                var bestScore = Float.NEGATIVE_INFINITY
                for (i in 0 until pieces.lastIndex) {
                    val score = scores[pieces[i] + pieces[i + 1]] ?: continue
                    if (score > bestScore) {
                        bestIndex = i
                        bestScore = score
                    }
                }
                if (bestIndex < 0) break
                pieces[bestIndex] += pieces.removeAt(bestIndex + 1)
            }
            require(pieces.all { it in scores }) { "Patient name cannot be encoded by the local model." }
            pieces.joinToString(" ") + " @name$index"
        }.joinToString("/")
    }
}
