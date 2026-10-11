package com.carelipik.app.domain.prescription

import com.carelipik.app.domain.model.MedicationDraft

/** Extracts explicit medicine mentions only. It never maps symptoms to treatment. */
class TranscriptMedicineSuggestions {
    fun extract(transcript: String, extracted: List<MedicationDraft> = emptyList()): List<MedicationDraft> {
        fun supported(value: String): String = value.takeIf { it.isNotBlank() &&
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                .containsMatchIn(transcript) }.orEmpty()
        val suggestions = extracted.filter { supported(it.name).isNotBlank() && it.name.lowercase() !in excludedNames }
            .map { medicine -> medicine.copy(
                strength = supported(medicine.strength),
                dose = supported(medicine.dose),
                frequency = supported(medicine.frequency),
                duration = supported(medicine.duration),
                instructions = supported(medicine.instructions),
                route = supported(medicine.route),
                genericName = supported(medicine.genericName),
                isDoctorReviewed = false,
                sourceEvidence = transcript.lineSequence().firstOrNull {
                    it.contains(medicine.name, ignoreCase = true)
                }.orEmpty()
            ) }.toMutableList()
        transcript.split(Regex("[\\n.!?]+\\s*")).forEach { sentence ->
            val matches = mention.findAll(sentence).toList()
            matches.forEachIndexed { index, match ->
                val name = match.groupValues[1]
                if (name.lowercase() in excludedNames) return@forEachIndexed
                val evidence = sentence.trim()
                val details = sentence.substring(match.range.first,
                    matches.getOrNull(index + 1)?.range?.first ?: sentence.length)
                suggestions += MedicationDraft(
                    name = name,
                    strength = strength.find(details)?.value.orEmpty(),
                    // A strength is not a dose. Leave anything not explicitly stated empty.
                    dose = dose.find(details)?.value.orEmpty(),
                    frequency = times.findAll(details).joinToString(", ") { it.value }
                        .ifBlank { frequency.find(details)?.value.orEmpty() },
                    duration = days.find(details)?.groupValues?.get(1)?.let { "$it days" }.orEmpty(),
                    sourceEvidence = evidence,
                    isDoctorReviewed = false
                )
            }
        }
        return suggestions.groupBy { it.name.trim().lowercase() }.values.map { candidates ->
            candidates.reduce { current, next -> current.copy(
                strength = current.strength.ifBlank { next.strength },
                dose = current.dose.ifBlank { next.dose },
                frequency = current.frequency.ifBlank { next.frequency },
                duration = current.duration.ifBlank { next.duration },
                instructions = current.instructions.ifBlank { next.instructions },
                route = current.route.ifBlank { next.route },
                genericName = current.genericName.ifBlank { next.genericName }
            ) }
        }
    }

    /** Recovers explicit plan mentions, but validates every candidate against the consultation. */
    fun extractFromPlan(transcript: String, plan: String): List<MedicationDraft> {
        val matches = Regex("\\b([\\p{L}][\\p{L}\\p{N}-]+)\\s+(\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|g|ml))\\b",
            RegexOption.IGNORE_CASE).findAll(plan).toList()
        val namedStrengths = matches.mapIndexed { index, match ->
                val end = matches.getOrNull(index + 1)?.range?.first ?: plan.length
                val details = plan.substring(match.range.first, end).split(Regex("(?<=[.!?])\\s+|[\\n;]+")).first()
                MedicationDraft(name = match.groupValues[1], strength = match.groupValues[2],
                    dose = dose.find(details)?.value.orEmpty(),
                    frequency = times.findAll(details).joinToString(", ") { it.value }
                        .ifBlank { frequency.find(details)?.value.orEmpty() },
                    duration = days.find(details)?.groupValues?.get(1)?.let { "$it days" }.orEmpty())
            }
        return extract(transcript, extract(plan, namedStrengths))
    }

    private companion object {
        val mention = Regex("\\b(?:tablet|tab|capsule|cap|syrup|medicine|prescribe|prescribed)\\s+([\\p{L}][\\p{L}\\p{N}-]*)", RegexOption.IGNORE_CASE)
        val strength = Regex("\\b\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|g|ml)\\b", RegexOption.IGNORE_CASE)
        val dose = Regex("\\b(?:\\d+(?:\\.\\d+)?|one|two|half)\\s*(?:tablets?|capsules?|teaspoons?|ml)\\b", RegexOption.IGNORE_CASE)
        val times = Regex("\\b(?:morning|afternoon|evening|night|bedtime|breakfast|lunch|dinner)\\b", RegexOption.IGNORE_CASE)
        val frequency = Regex("\\b(?:once|twice|three times|four times)\\s+(?:a day|daily)|\\bevery\\s+\\d+\\s+hours\\b", RegexOption.IGNORE_CASE)
        val days = Regex("\\bfor\\s+(\\d+|one|two|three|four|five|six|seven|ten)\\s+days?\\b", RegexOption.IGNORE_CASE)
        val excludedNames = setOf("for", "of", "the", "a", "an", "is", "was", "and", "to", "should", "rest", "water", "daily", "twice", "once", "tablet", "tablets", "tab", "capsule", "capsules", "cap", "syrup", "medicine", "medicines", "dose", "dosage")
    }
}
