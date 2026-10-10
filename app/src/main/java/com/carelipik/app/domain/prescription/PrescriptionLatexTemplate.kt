package com.carelipik.app.domain.prescription

/** A common, editable XeLaTeX prescription template. All user text is escaped as data. */
object PrescriptionLatexTemplate {
    fun render(prescription: Prescription): String = buildString {
        appendLine("\\documentclass[11pt,a4paper]{article}")
        appendLine("\\usepackage[margin=20mm]{geometry}")
        appendLine("\\usepackage{fontspec}")
        appendLine("\\setmainfont[BoldFont=NotoSans-Bold.ttf]{NotoSans-Regular.ttf}")
        appendLine("\\newfontfamily\\hindifont{NotoSansDevanagari-Regular.ttf}")
        appendLine("\\usepackage{longtable,array}")
        appendLine("\\setlength{\\parindent}{0pt}")
        appendLine("\\begin{document}")
        appendLine("{\\Large\\textbf{${escape(prescription.clinicName)}}}\\par")
        appendLine("${escape(prescription.doctorName)}\\par")
        appendLine("Registration: ${escape(prescription.registrationNumber)}\\par")
        appendLine("\\bigskip\\hrule\\bigskip")
        appendLine("{\\Large\\textbf{Prescription}}\\par")
        appendLine("Date: ${escape(prescription.date)}\\par")
        appendLine("Patient: ${escape(prescription.patientName)}\\par")
        appendLine("Age: ${escape(prescription.patientAge)}\\par\\bigskip")
        appendLine("\\begin{longtable}{p{0.06\\linewidth}p{0.66\\linewidth}p{0.15\\linewidth}}")
        appendLine("\\textbf{Rx} & \\textbf{Medicine and directions} & \\textbf{Quantity} \\\\ \\hline")
        appendLine("\\endhead")
        prescription.medicines.forEachIndexed { index, item ->
            val med = item.medicine
            val directions = listOf("Strength" to med.strength, "Dose" to med.dose,
                "Route" to med.route, "Frequency" to med.frequency, "Duration" to med.duration,
                "Instructions" to med.instructions).filter { it.second.isNotBlank() }
                .joinToString("; ") { "${it.first}: ${it.second}" }
            appendLine("${index + 1} & \\textbf{${escape(med.name)}}${if (med.genericName.isBlank()) "" else " (${escape(med.genericName)})"} \\newline ${escape(directions)} & ${escape(item.quantity)} \\\\ \\hline")
        }
        appendLine("\\end{longtable}")
        appendLine("\\textbf{Advice}\\par ${escape(prescription.advice)}\\par\\bigskip")
        appendLine("\\textbf{Follow-up}\\par ${escape(prescription.followUp)}\\par")
        appendLine("\\vspace{20mm}Doctor signature: \\rule{65mm}{0.4pt}")
        appendLine("\\end{document}")
    }

    private fun escape(text: String): String = buildString {
        text.forEach { char -> append(when (char) {
            '\\' -> "\\textbackslash{}"
            '{' -> "\\{"
            '}' -> "\\}"
            '$' -> "\\$"
            '&' -> "\\&"
            '#' -> "\\#"
            '%' -> "\\%"
            '_' -> "\\_"
            '^' -> "\\textasciicircum{}"
            '~' -> "\\textasciitilde{}"
            '\n' -> "\\newline "
            '\r' -> ""
            else -> char.toString()
        }) }
    }.replace(Regex("[\\u0900-\\u097F]+")) { "{\\hindifont ${it.value}}" }
}
