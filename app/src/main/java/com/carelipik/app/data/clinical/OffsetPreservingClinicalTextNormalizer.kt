package com.carelipik.app.data.clinical

import com.carelipik.app.domain.clinical.ClinicalTextNormalizer
import com.carelipik.app.domain.clinical.ClinicalTransliterator
import com.carelipik.app.domain.clinical.NoOpClinicalTransliterator
import com.carelipik.app.domain.clinical.NormalizedClinicalText
import java.text.Normalizer

/** Creates a model/search representation without modifying the doctor-visible transcript. */
class OffsetPreservingClinicalTextNormalizer(
    private val transliterator: ClinicalTransliterator = NoOpClinicalTransliterator
) : ClinicalTextNormalizer {
    override fun normalize(text: String): NormalizedClinicalText {
        if (text.isEmpty()) {
            return NormalizedClinicalText(text, "", emptyList(), emptyList())
        }
        val lookup = StringBuilder(text.length)
        val offsets = mutableListOf<Int>()
        var previousWasSpace = false

        text.forEachIndexed { index, originalCharacter ->
            val normalizedCharacters = Normalizer.normalize(
                originalCharacter.toString(),
                Normalizer.Form.NFKC
            )
            normalizedCharacters.forEach { normalizedCharacter ->
                val canonical = normalizedCharacter.canonicalClinicalCharacter()
                when {
                    canonical.isWhitespace() -> {
                        if (lookup.isNotEmpty() && !previousWasSpace) {
                            lookup.append(' ')
                            offsets += index
                            previousWasSpace = true
                        }
                    }
                    canonical.isISOControl() -> Unit
                    else -> {
                        lookup.append(canonical.lowercaseChar())
                        offsets += index
                        previousWasSpace = false
                    }
                }
            }
        }
        if (lookup.lastOrNull() == ' ') {
            lookup.deleteCharAt(lookup.lastIndex)
            offsets.removeAt(offsets.lastIndex)
        }
        return NormalizedClinicalText(
            originalText = text,
            lookupText = lookup.toString(),
            originalOffsets = offsets,
            transliterations = transliterator.transliterate(text).filter { candidate ->
                candidate.originalStart >= 0 &&
                    candidate.originalEndExclusive in
                    (candidate.originalStart + 1)..text.length &&
                    candidate.value.isNotBlank()
            }
        )
    }

    private fun Char.canonicalClinicalCharacter(): Char = when (this) {
        '\u2018', '\u2019', '\u201A', '\u201B' -> '\''
        '\u201C', '\u201D', '\u201E', '\u201F' -> '"'
        '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212' -> '-'
        '\u00A0', '\u2007', '\u202F' -> ' '
        else -> this
    }
}
