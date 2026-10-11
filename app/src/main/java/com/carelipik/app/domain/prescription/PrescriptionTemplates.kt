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
    const val CONTENT_START = "% CARELIPIK PRESCRIPTION CONTENT BEGIN"
    const val CONTENT_END = "% CARELIPIK PRESCRIPTION CONTENT END"
    const val MEDICINES_START = "% CARELIPIK MEDICINES BEGIN"
    const val MEDICINES_END = "% CARELIPIK MEDICINES END"
    const val ADVICE_START = "% CARELIPIK ADVICE BEGIN"
    const val ADVICE_END = "% CARELIPIK ADVICE END"
    const val DETAILS_START = "% CARELIPIK DETAILS BEGIN"
    const val DETAILS_END = "% CARELIPIK DETAILS END"
    const val DOCTOR = "% CARELIPIK DOCTOR DETAILS %"
    const val PATIENT = "% CARELIPIK PATIENT DETAILS %"
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
        require(template.length <= 100_000) { "The LaTeX template is too large." }
        val begin = "\\begin{document}"
        val end = "\\end{document}"
        val start = template.indexOf(begin)
        val finish = template.indexOf(end)
        require(template.contains("\\documentclass") && start >= 0 && finish > start &&
            template.split(begin).size == 2 && template.split(end).size == 2) {
            "Use one complete LaTeX document with a document class."
        }
        fun validMarker(marker: String): Boolean = template.split(marker).size == 2 &&
            template.indexOf(marker) > start && template.indexOf(marker) < finish &&
            template.lineSequence().count { it.trim() == marker } == 1
        require(validMarker(BODY)) { "Put exactly one $BODY marker on its own line inside the document." }
        listOf(DOCTOR, PATIENT).forEach { marker ->
            require(!template.contains(marker) || validMarker(marker)) {
                "Put each doctor/patient placeholder on its own line inside the document."
            }
        }
        return template
    }

    fun validateReusableLetterhead(template: String): String {
        validate(template)
        require(template.contains(DOCTOR) && template.contains(PATIENT)) {
            "The generated letterhead must contain doctor, patient and prescription placeholders."
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

    fun create(template: String?, draft: ClinicalDraft, patientName: String, doctor: String,
        doctorSpecialty: String = "", registrationNumber: String = "", clinicName: String = ""): String {
        val selected = template?.takeIf { runCatching { validate(it) }.isSuccess } ?: common
        val doctorDetails = buildString {
            append("\\noindent Doctor: ${escape(doctor)}\\par\n")
            if (doctorSpecialty.isNotBlank()) append("Specialty: ${escape(doctorSpecialty)}\\par\n")
            if (registrationNumber.isNotBlank()) append("Registration: ${escape(registrationNumber)}\\par\n")
            if (clinicName.isNotBlank()) append("Clinic: ${escape(clinicName)}\\par\n")
        }
        val patientDetails = "\\noindent Patient: ${escape(patientName)}\\quad Age: ${escape(draft.patientAge)}\\par\n"
        val body = buildString {
            if (!selected.contains(DOCTOR)) append(doctorDetails)
            if (!selected.contains(PATIENT)) append(patientDetails)
            append(CONTENT_START + "\n")
            append(DETAILS_START + "\n" + DETAILS_END + "\n")
            append(MEDICINES_START + "\n" + medicineSection(emptyList()) + MEDICINES_END + "\n")
            append(ADVICE_START + "\n" + ADVICE_END + "\n")
            append("\\vfill\\noindent Doctor signature: \\rule{6cm}{0.4pt}\\par\n")
            append(CONTENT_END + "\n")
        }
        return selected.replace(DOCTOR, doctorDetails).replace(PATIENT, patientDetails).replace(BODY, body)
    }

    /** Retains the doctor's prescription content while replacing the surrounding letterhead. */
    fun applyLetterhead(source: String, replacement: String): String {
        fun contentRange(value: String): IntRange {
            val start = value.indexOf(CONTENT_START)
            val end = value.indexOf(CONTENT_END)
            require(start >= 0 && end > start && value.split(CONTENT_START).size == 2 &&
                value.split(CONTENT_END).size == 2) {
                "Letterhead saved for new prescriptions. Keep the prescription content markers to apply it to the current prescription."
            }
            return (start + CONTENT_START.length) until end
        }
        val original = contentRange(source)
        return replacement.replaceRange(contentRange(replacement), source.substring(original))
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
