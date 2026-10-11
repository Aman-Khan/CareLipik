package com.carelipik.app.data.transcription

import org.json.JSONObject

/**
 * Small, deliberately scoped SentencePiece Unigram tokenizer for the exported Apollo bundle.
 *
 * The model's tokenizer.json is installed beside the model, rather than being baked into the APK.
 * This keeps the large, replaceable model bundle out of source control. Offsets always refer to
 * the original transcript so a highlighted candidate can be verified in the recording.
 */
internal class ApolloSentencePieceTokenizer(tokenizerJson: String) {
    private val vocabulary: Map<String, Token> = parseVocabulary(JSONObject(tokenizerJson))
    private val unknownId = vocabulary["[UNK]"]?.id ?: vocabulary["<unk>"]?.id
        ?: error("Apollo tokenizer has no unknown token.")
    private val clsId = vocabulary["[CLS]"]?.id ?: error("Apollo tokenizer has no [CLS] token.")
    private val sepId = vocabulary["[SEP]"]?.id ?: error("Apollo tokenizer has no [SEP] token.")
    private val padId = vocabulary["[PAD]"]?.id ?: 0

    fun encode(text: String, maximumTokens: Int): ApolloTokenizedInput {
        require(maximumTokens >= 3) { "maximumTokens must allow special tokens." }
        val pieces = mutableListOf<ApolloTokenPiece>()
        val matcher = NON_WHITESPACE.findAll(text)
        for (match in matcher) {
            if (pieces.size >= maximumTokens - 2) break
            pieces += tokenizeWord(match.value, match.range.first, maximumTokens - 2 - pieces.size)
        }
        val ids = LongArray(pieces.size + 2)
        ids[0] = clsId.toLong()
        pieces.forEachIndexed { index, piece -> ids[index + 1] = piece.id.toLong() }
        ids[ids.lastIndex] = sepId.toLong()
        return ApolloTokenizedInput(ids, pieces, padId)
    }

    private fun tokenizeWord(word: String, originalStart: Int, capacity: Int): List<ApolloTokenPiece> {
        if (capacity <= 0) return emptyList()
        // SentencePiece encodes word boundaries with ▁. Keeping original character offsets avoids
        // normalising or silently rewriting the doctor's transcript.
        val source = "▁$word"
        val best = Array(source.length + 1) { Candidate.impossible() }
        best[0] = Candidate(score = 0.0, previous = -1, token = null)
        for (start in source.indices) {
            val current = best[start]
            if (current.token == null && start != 0) continue
            for (end in (start + 1)..source.length) {
                val token = vocabulary[source.substring(start, end)] ?: continue
                val score = current.score + token.score
                if (score > best[end].score) {
                    best[end] = Candidate(score, start, token)
                }
            }
            // Preserve an unknown character as one token when no vocabulary piece begins here.
            if (source.substring(start, start + 1) !in vocabulary) {
                val end = start + 1
                if (current.score - UNKNOWN_PENALTY > best[end].score) {
                    best[end] = Candidate(current.score - UNKNOWN_PENALTY, start, Token("[UNK]", unknownId, -UNKNOWN_PENALTY))
                }
            }
        }
        if (best.last().previous < 0) {
            return listOf(ApolloTokenPiece(unknownId, originalStart, originalStart + word.length))
        }
        val reversed = mutableListOf<ApolloTokenPiece>()
        var cursor = source.length
        while (cursor > 0) {
            val candidate = best[cursor]
            val previous = candidate.previous
            val sourceStart = previous.coerceAtLeast(0)
            val tokenStart = (sourceStart - 1).coerceAtLeast(0)
            val tokenEnd = (cursor - 1).coerceAtLeast(tokenStart)
            // ▁ is an inserted boundary marker; it must not extend a transcript span backwards.
            val originalTokenStart = originalStart + tokenStart.coerceAtMost(word.length)
            val originalTokenEnd = originalStart + tokenEnd.coerceAtMost(word.length)
            reversed += ApolloTokenPiece(
                id = candidate.token?.id ?: unknownId,
                startIndex = originalTokenStart,
                endIndexExclusive = originalTokenEnd.coerceAtLeast(originalTokenStart + 1)
            )
            cursor = previous
        }
        return reversed.asReversed().take(capacity)
    }

    private fun parseVocabulary(root: JSONObject): Map<String, Token> {
        val entries = root.getJSONObject("model").getJSONArray("vocab")
        require(root.getJSONObject("model").getString("type") == "Unigram") {
            "Apollo bundle requires a Unigram tokenizer."
        }
        return buildMap {
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONArray(index)
                val text = entry.getString(0)
                put(text, Token(text, index, entry.getDouble(1)))
            }
            val added = root.optJSONArray("added_tokens")
            if (added != null) for (index in 0 until added.length()) {
                val entry = added.getJSONObject(index)
                val text = entry.getString("content")
                put(text, Token(text, entry.getInt("id"), 0.0))
            }
        }
    }

    private data class Token(val text: String, val id: Int, val score: Double)
    private data class Candidate(val score: Double, val previous: Int, val token: Token?) {
        companion object {
            fun impossible() = Candidate(Double.NEGATIVE_INFINITY, -1, null)
        }
    }

    private companion object {
        val NON_WHITESPACE = Regex("\\S+")
        const val UNKNOWN_PENALTY = 20.0
    }
}

internal data class ApolloTokenPiece(
    val id: Int,
    val startIndex: Int,
    val endIndexExclusive: Int
)

internal data class ApolloTokenizedInput(
    val ids: LongArray,
    val pieces: List<ApolloTokenPiece>,
    val padId: Int
)
