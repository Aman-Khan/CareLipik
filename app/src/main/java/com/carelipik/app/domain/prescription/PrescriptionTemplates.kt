package com.carelipik.app.domain.prescription

import com.carelipik.app.domain.model.ClinicalDraft

fun interface LetterheadConversionEngine {
    fun convert(pdf: ByteArray): String
}

interface LetterheadRepository {
    suspend fun load(): String?
    suspend fun save(template: String)
}

object PrescriptionTemplates {
    const val MEDICINES_START = "% CARELIPIK MEDICINES BEGIN"
    const val MEDICINES_END = "% CARELIPIK MEDICINES END"
    const val ADVICE_START = "% CARELIPIK ADVICE BEGIN"
    const val ADVICE_END = "% CARELIPIK ADVICE END"
    const val DETAILS_START = "% CARELIPIK DETAILS BEGIN"
    const val DETAILS_END = "% CARELIPIK DETAILS END"
    const val BODY = "%%CARE LIPIK PRESCRIPTION%%"
    val common = """
        \documentclass[12pt]{article}
        \usepackage[a4paper,margin=25mm]{geometry}
        \usepackage[T1]{fontenc}
        \begin{document}
        \begin{center}\Large Prescription\end{center}
        $BODY
        \end{document}
    """.trimIndent()

    fun validate(template: String): String {
        require(template.length <= 100_000 && template.contains("\\begin{document}") &&
            template.contains("\\end{document}") && template.split(BODY).size == 2) {
            "Use a complete LaTeX document with exactly one $BODY marker."
        }
        return template
    }

    fun escape(value: String): String = buildString {
        value.forEach { append(when (it) {
            '\\' -> "\\textbackslash{}"
            '&', '%', '$', '#', '_', '{', '}' -> "\\$it"
            '~' -> "\\textasciitilde{}"
            '^' -> "\\textasciicircum{}"
            '\n' -> "\\par "
            '\r' -> ""
            else -> it.toString()
        }) }
    }

    fun create(template: String?, draft: ClinicalDraft, patientName: String, doctor: String): String {
        val selected = template?.takeIf { runCatching { validate(it) }.isSuccess } ?: common
        val body = buildString {
            append("\\noindent Doctor: ${escape(doctor)}\\par\n")
            append("\\noindent Patient: ${escape(patientName)}\\quad Age: ${escape(draft.patientAge)}\\par\n")
            append(DETAILS_START + "\n" + DETAILS_END + "\n")
            append(MEDICINES_START + "\n" + medicineSection(emptyList()) + MEDICINES_END + "\n")
            append(ADVICE_START + "\n" + ADVICE_END + "\n")
            append("\\vfill\\noindent Doctor signature: \\rule{6cm}{0.4pt}\\par\n")
        }
        return selected.replace(BODY, body)
    }

    /** Updates only the managed medicine section, retaining other doctor's source edits. */
    fun updateMedicines(source: String, medicines: List<com.carelipik.app.domain.model.MedicationDraft>): String {
        val start = source.indexOf(MEDICINES_START)
        val end = source.indexOf(MEDICINES_END)
        require(start >= 0 && end > start && source.split(MEDICINES_START).size == 2 &&
            source.split(MEDICINES_END).size == 2) {
            "Keep the medicine section markers in the LaTeX source to sync medicines."
        }
        return source.substring(0, start + MEDICINES_START.length) + "\n" + medicineSection(medicines) +
            source.substring(end)
    }

    fun updateAdvice(source: String, advice: List<PrescriptionAdviceSuggestion>): String {
        val start = source.indexOf(ADVICE_START)
        val end = source.indexOf(ADVICE_END)
        require(start >= 0 && end > start && source.split(ADVICE_START).size == 2 &&
            source.split(ADVICE_END).size == 2) { "Keep the advice section markers to sync advice." }
        val body = if (advice.isEmpty()) "" else buildString {
            append("\\section*{Advice and follow-up}\n\\begin{itemize}\n")
            advice.forEach { append("\\item ${escape(it.content)}\n") }
            append("\\end{itemize}\n")
        }
        return source.substring(0, start + ADVICE_START.length) + "\n" + body + source.substring(end)
    }

    fun updateDetails(source: String, details: Map<PrescriptionDetail, String>): String {
        val start = source.indexOf(DETAILS_START)
        val end = source.indexOf(DETAILS_END)
        require(start >= 0 && end > start && source.split(DETAILS_START).size == 2 &&
            source.split(DETAILS_END).size == 2) { "Keep the detail section markers to sync prescription fields." }
        val body = buildString {
            PrescriptionDetail.entries.forEach { field ->
                val value = details[field].orEmpty()
                if (value.isNotBlank()) append("\\section*{${escape(field.label)}}\n${escape(value)}\\par\n")
            }
        }
        return source.substring(0, start + DETAILS_START.length) + "\n" + body + source.substring(end)
    }

    private fun medicineSection(medicines: List<com.carelipik.app.domain.model.MedicationDraft>): String = buildString {
        append("\\section*{Prescribed medicines}\n")
        if (medicines.isEmpty()) append("No medicines added by the doctor.\\par\n")
        else {
            append("\\begin{enumerate}\n")
            medicines.forEach { m ->
                fun field(value: String) = escape(value.ifBlank { "Doctor to complete" })
                append("\\item \\textbf{${field(m.name)}}")
                if (m.strength.isNotBlank()) append(" (${escape(m.strength)})")
                append("\\par Dose: ${field(m.dose)}")
                append("\\par Times of day / frequency: ${field(m.frequency)}")
                append("\\par Duration: ${field(m.duration)}")
                if (m.instructions.isNotBlank()) append("\\par Instructions: ${escape(m.instructions)}")
                append("\\par\n")
            }
            append("\\end{enumerate}\n")
        }
    }

}
