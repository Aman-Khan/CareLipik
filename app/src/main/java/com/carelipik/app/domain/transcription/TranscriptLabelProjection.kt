package com.carelipik.app.domain.transcription

/** Changes displayed headers without changing speaker IDs or model correction offsets. */
class TranscriptLabelProjection(
    private val source: String,
    labels: Map<String, String>
) {
    private data class Header(val start: Int, val end: Int, val original: String, val label: String)
    private val headers = Regex("(?m)^(Speaker\\s+[A-Za-z0-9._-]+|Person\\s+[A-Za-z0-9._-]+|Doctor|Patient)(?=\\s*:)",
        RegexOption.IGNORE_CASE).findAll(source).mapNotNull { match ->
        val id = match.value.lowercase().replace(Regex("^person\\s+"), "speaker ")
            .replace(Regex("\\s+"), "-")
        labels[id]?.let { Header(match.range.first, match.range.last + 1, match.value, it) }
    }.toList()

    val text: String = buildString {
        var cursor = 0
        headers.forEach {
            append(source.substring(cursor, it.start))
            append(it.label)
            cursor = it.end
        }
        append(source.substring(cursor))
    }

    fun displayOffset(sourceOffset: Int): Int = sourceOffset + headers
        .filter { it.end <= sourceOffset }.sumOf { it.label.length - (it.end - it.start) }

    fun restore(editedText: String): String {
        val originals = headers.associate { it.label to it.original }
        if (originals.isEmpty()) return editedText
        val pattern = Regex("(?m)^(${originals.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }})(?=\\s*:)")
        return pattern.replace(editedText) { originals.getValue(it.value) }
    }
}
