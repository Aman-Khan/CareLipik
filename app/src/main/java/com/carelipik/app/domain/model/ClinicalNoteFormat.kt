package com.carelipik.app.domain.model

data class ClinicalNoteSectionDefinition(
    val id: String,
    val title: String
)

private fun noteSections(vararg values: String): List<ClinicalNoteSectionDefinition> =
    values.toList().chunked(2).map { (id, title) ->
        ClinicalNoteSectionDefinition(id, title)
    }

enum class ClinicalNoteFormat(
    val displayName: String,
    val description: String,
    val sectionDefinitions: List<ClinicalNoteSectionDefinition>
) {
    Soap(
        displayName = "SOAP",
        description = "General OPD and follow-up: Subjective, Objective, Assessment, Plan.",
        sectionDefinitions = noteSections("subjective", "Subjective", "objective", "Objective", "assessment", "Assessment", "plan", "Plan")
    ),
    Apso(
        displayName = "APSO",
        description = "Follow-up note with Assessment and Plan shown first.",
        sectionDefinitions = noteSections("assessment", "Assessment", "plan", "Plan", "subjective", "Subjective", "objective", "Objective")
    ),
    HistoryAndPhysical(
        displayName = "H&P",
        description = "Comprehensive first consultation history and examination.",
        sectionDefinitions = noteSections(
            "chief_complaint", "Chief complaint",
            "history_present_illness", "History of present illness",
            "past_history", "Past medical and surgical history",
            "medication_history", "Medication history",
            "allergies", "Allergies",
            "family_social_history", "Family and social history",
            "review_systems", "Review of systems",
            "examination", "Examination and objective findings",
            "assessment", "Assessment",
            "plan", "Plan"
        )
    ),
    ProblemOriented(
        displayName = "Problem-oriented note",
        description = "Multiple problems with findings, assessment, and plan.",
        sectionDefinitions = noteSections(
            "problem_list", "Problem list",
            "problem_findings", "Problem-specific findings",
            "assessment", "Assessment by problem",
            "plan", "Plan by problem"
        )
    ),
    Progress(
        displayName = "Progress note",
        description = "Repeat visit or inpatient interval history, progress, and plan.",
        sectionDefinitions = noteSections(
            "interval_history", "Interval history",
            "current_findings", "Current findings",
            "progress", "Clinical progress",
            "assessment", "Assessment",
            "plan", "Plan"
        )
    ),
    Dap(
        displayName = "DAP",
        description = "Mental health, counselling, and allied healthcare documentation.",
        sectionDefinitions = noteSections("data", "Data", "assessment", "Assessment", "plan", "Plan")
    ),
    Birp(
        displayName = "BIRP",
        description = "Behavioural and psychiatric therapy documentation.",
        sectionDefinitions = noteSections("behaviour", "Behaviour", "intervention", "Intervention", "response", "Response", "plan", "Plan")
    ),
    Girp(
        displayName = "GIRP",
        description = "Goal-based therapy and rehabilitation documentation.",
        sectionDefinitions = noteSections("goal", "Goal", "intervention", "Intervention", "response", "Response", "plan", "Plan")
    ),
    Procedure(
        displayName = "Procedure note",
        description = "Indication, consent, procedure, findings, complications, and aftercare.",
        sectionDefinitions = noteSections(
            "indication", "Indication",
            "consent", "Consent",
            "preparation", "Preparation and anaesthesia",
            "procedure", "Procedure performed",
            "findings", "Findings",
            "complications", "Complications",
            "aftercare", "Aftercare and follow-up"
        )
    ),
    CustomSpecialty(
        displayName = "Custom specialty note",
        description = "Doctor-specialty structure for focused consultations.",
        sectionDefinitions = noteSections(
            "chief_concern", "Chief concern",
            "specialty_history", "Specialty history",
            "specialty_findings", "Specialty examination and findings",
            "assessment", "Assessment",
            "plan", "Plan"
        )
    );

    companion object {
        fun fromStorage(value: String): ClinicalNoteFormat = entries
            .firstOrNull { it.name == value }
            ?: Soap
    }
}

enum class ClinicalNoteLanguage(val displayName: String) {
    Original("Consultation language"),
    English("English");

    companion object {
        fun fromStorage(value: String): ClinicalNoteLanguage = entries
            .firstOrNull { it.name == value }
            ?: Original
    }
}

enum class ClinicalNoteGenerationSource(val displayName: String) {
    OfflineTranscript("Offline transcript-backed draft"),
    LocalQwen("Local Qwen3 structured draft"),
    Gemini("Gemini structured draft");

    companion object {
        fun fromStorage(value: String): ClinicalNoteGenerationSource = entries
            .firstOrNull { it.name == value }
            ?: OfflineTranscript
    }
}

data class ClinicalNoteSection(
    val id: String,
    val title: String,
    val content: String = "",
    val sourceTurnIds: List<String> = emptyList()
)
