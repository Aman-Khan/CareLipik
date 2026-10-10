package com.carelipik.app.domain.clinical

data class NormalizedClinicalText(
    val originalText: String,
    val lookupText: String,
    val originalOffsets: List<Int>,
    val transliterations: List<ClinicalTransliteration> = emptyList()
) {
    init {
        require(lookupText.length == originalOffsets.size) {
            "Every normalized character must map to an original transcript offset."
        }
        require(originalOffsets.all { it in originalText.indices }) {
            "Normalized offsets must point inside the original transcript."
        }
    }

    fun originalRange(normalizedStart: Int, normalizedEndExclusive: Int): IntRange {
        require(normalizedStart in lookupText.indices)
        require(normalizedEndExclusive in (normalizedStart + 1)..lookupText.length)
        return originalOffsets[normalizedStart]..originalOffsets[normalizedEndExclusive - 1]
    }
}

data class ClinicalTransliteration(
    val originalStart: Int,
    val originalEndExclusive: Int,
    val value: String,
    val languageCode: String,
    val confidence: Float? = null
)

fun interface ClinicalTransliterator {
    fun transliterate(originalText: String): List<ClinicalTransliteration>
}

object NoOpClinicalTransliterator : ClinicalTransliterator {
    override fun transliterate(originalText: String): List<ClinicalTransliteration> = emptyList()
}

fun interface ClinicalTextNormalizer {
    fun normalize(text: String): NormalizedClinicalText
}
