package com.carelipik.app.domain.prescription

data class PrescriptionPreviewBlock(val text: String, val heading: Boolean = false)

/** Reads the supported text commands; it does not execute arbitrary LaTeX. */
object PrescriptionPreviewContent {
    fun blocks(source: String): List<PrescriptionPreviewBlock> {
        require(source.contains("\\begin{document}") && source.contains("\\end{document}"))
        val body = source.substringAfter("\\begin{document}").substringBefore("\\end{document}")
        val sections = Regex("\\\\section\\*?\\{([^{}]*)\\}").findAll(body).toList()
        val result = mutableListOf<PrescriptionPreviewBlock>()
        var position = 0
        for (section in sections) {
            plain(body.substring(position, section.range.first)).takeIf(String::isNotBlank)
                ?.let { result += PrescriptionPreviewBlock(it) }
            result += PrescriptionPreviewBlock(plain(section.groupValues[1]), heading = true)
            position = section.range.last + 1
        }
        plain(body.substring(position)).takeIf(String::isNotBlank)
            ?.let { result += PrescriptionPreviewBlock(it) }
        return result
    }

    private fun plain(value: String): String {
        val literals = mutableListOf<String>()
        fun protect(literal: String): String {
            literals += literal
            return "\uE000${literals.lastIndex}\uE001"
        }
        var text = Regex("\\\\(?:textbackslash|textasciitilde|textasciicircum)\\{\\}").replace(value) {
            protect(when { it.value.contains("textbackslash") -> "\\"; it.value.contains("textasciitilde") -> "~"; else -> "^" })
        }
        text = Regex("\\\\([&%$#_{}])").replace(text) { protect(it.groupValues[1]) }
        text = text.lineSequence().joinToString("\n") { it.substringBefore('%') }
        text = Regex("\\\\(?:begin|end)\\{[^}]*\\}").replace(text, "\n")
        text = Regex("\\\\(?:rule|includegraphics|hspace|vspace|textcolor|color|fontsize)(?:\\[[^\\]]*\\])?\\{[^}]*\\}(?:\\{[^}]*\\})?").replace(text, "")
        text = Regex("\\\\(?:par|newline|vfill)\\b|\\\\\\\\").replace(text, "\n")
        text = Regex("\\\\(?:quad|qquad)\\b").replace(text, "   ")
        text = Regex("\\\\item\\b").replace(text, "\n• ")
        text = Regex("\\\\[a-zA-Z]+\\*?(?:\\[[^\\]]*\\])?").replace(text, "")
        text = text.replace("{", "").replace("}", "")
        text = Regex("\uE000(\\d+)\uE001").replace(text) { literals[it.groupValues[1].toInt()] }
        return text.lineSequence().map(String::trim).filter(String::isNotBlank).joinToString("\n")
    }
}
