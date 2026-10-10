package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import org.json.JSONArray
import org.json.JSONObject

internal object MedGemmaPromptBuilder {
    fun build(request: ClinicalNoteGenerationRequest): String {
        val indexedTranscript = indexTurns(request.reviewedTranscript)
        val requiredSections = request.noteFormat.sectionDefinitions.joinToString(",") { it.id }
        return """
            Create an UNVERIFIED clinical note using ONLY the transcript. No diagnosis,
            prescription, examination finding, or missing fact may be inferred. A question is not
            evidence. Preserve negation, uncertainty, time, speaker, and medication status.
            In prescribed_medications include only medicines explicitly prescribed/recommended by
            the doctor now; exclude existing medicines and pharmacy suggestions.
            Return one COMPACT JSON object only, without markdown or repeated facts:
            {"visit_reason":"","sections":[{"id":"","title":"","content":"","source_turn_ids":["T1"]}],
            "prescribed_medications":[{"name":"","generic_name":"","strength":"",
            "dose":"","route":"","frequency":"","duration":"","instructions":"",
            "source_turn_ids":["T1"],"source_evidence":""}],"coverage_warnings":[]}
            Valid section IDs: $requiredSections. Evidence IDs must directly support each claim.
            visit_reason must be a concise presenting symptom, injury, or concern with stated
            onset/duration only. Exclude greetings, names, staff actions, handoffs, and small talk.
            Empty unsupported fields. Language=${request.outputLanguage.displayName};
            format=${request.noteFormat.displayName}; specialty=${request.specialtyName.ifBlank { "unknown" }};
            age=${request.patientAge.ifBlank { "unknown" }}; reason=${request.visitReason.ifBlank { "unknown" }}.
            Transcript:
            $indexedTranscript
        """.trimIndent()
    }

    fun normalizeJson(rawResponse: String, request: ClinicalNoteGenerationRequest): String {
        val raw = parseOrRecoverJson(rawResponse, request)
        val definitions = request.noteFormat.sectionDefinitions.associateBy { it.id }
        val validTurnIds = indexedTurnIds(request.reviewedTranscript)
        val sections = JSONArray()
        val warnings = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val rawSections = raw.optJSONArray("sections") ?: JSONArray()
        for (index in 0 until rawSections.length()) {
            val item = rawSections.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val definition = definitions[id] ?: continue
            if (!seen.add(id)) continue
            val sourceTurnIds = item.optJSONArray("source_turn_ids").validTurnIds(validTurnIds)
            val content = item.optString("content").trim()
            if (content.isNotBlank() && sourceTurnIds.length() == 0) {
                warnings += "${definition.title} was omitted because the on-device model provided no valid transcript evidence."
            }
            sections.put(
                JSONObject(item.toString())
                    .put("id", id)
                    .put("title", definition.title)
                    .put("content", if (sourceTurnIds.length() == 0) "" else content)
                    .put("source_turn_ids", sourceTurnIds)
            )
        }
        request.noteFormat.sectionDefinitions.filterNot { it.id in seen }.forEach { definition ->
            sections.put(
                JSONObject()
                    .put("id", definition.id)
                    .put("title", definition.title)
                    .put("content", "")
                    .put("source_turn_ids", JSONArray())
            )
        }
        val medications = JSONArray()
        val rawMedications = raw.optJSONArray("prescribed_medications") ?: JSONArray()
        for (index in 0 until rawMedications.length()) {
            val medication = rawMedications.optJSONObject(index) ?: continue
            val sourceTurnIds = medication.optJSONArray("source_turn_ids")
                .validTurnIds(validTurnIds)
            val name = medication.optString("name").trim()
            if (name.isNotBlank() && sourceTurnIds.length() == 0) {
                warnings += "$name requires doctor review because the on-device model provided no valid transcript evidence."
            }
            medications.put(
                JSONObject(medication.toString())
                    .put("source_turn_ids", sourceTurnIds)
                    .put("source_evidence", sourceTurnIds.toStringList().joinToString())
            )
        }
        val providerWarnings = raw.optJSONArray("coverage_warnings") ?: JSONArray()
        for (index in 0 until providerWarnings.length()) {
            providerWarnings.optString(index).trim().takeIf(String::isNotBlank)?.let(warnings::add)
        }
        return JSONObject()
            .put("visit_reason", raw.optString("visit_reason").trim())
            .put("sections", sections)
            .put("prescribed_medications", medications)
            .put("coverage_warnings", JSONArray(warnings.distinct()))
            .toString()
    }

    private fun indexTurns(transcript: String): String {
        return transcriptTurns(transcript)
            .mapIndexed { index, turn -> "[T${index + 1}] $turn" }
            .joinToString("\n")
    }

    private fun indexedTurnIds(transcript: String): Set<String> =
        transcriptTurns(transcript).indices.mapTo(mutableSetOf()) { "T${it + 1}" }

    private fun transcriptTurns(transcript: String): List<String> {
        val explicitTurns = Regex("(?m)(?=^(?:Doctor|Patient|Speaker\\s+[^:]+):)")
            .split(transcript.trim())
            .map(String::trim)
            .filter(String::isNotBlank)
        val turns = if (explicitTurns.size > 1) explicitTurns else transcript.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toList()
        return turns
    }

    private fun JSONArray?.validTurnIds(validIds: Set<String>): JSONArray = JSONArray(
        toStringList().filter { it in validIds }.distinct()
    )

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "The on-device model did not return a JSON object." }
        return raw.substring(start, end + 1)
    }

    private fun parseOrRecoverJson(
        rawResponse: String,
        request: ClinicalNoteGenerationRequest
    ): JSONObject {
        runCatching { JSONObject(extractJsonObject(rawResponse)) }.getOrNull()?.let { return it }

        // Small on-device models occasionally stop after producing complete section objects but
        // before closing the outer JSON. Recover only individually valid, known section objects;
        // never guess missing syntax or clinical content.
        val knownSectionIds = request.noteFormat.sectionDefinitions.mapTo(mutableSetOf()) { it.id }
        val recoveredSections = JSONArray()
        balancedJsonObjects(rawResponse).forEach { candidate ->
            val item = runCatching { JSONObject(candidate) }.getOrNull() ?: return@forEach
            if (item.optString("id") in knownSectionIds && item.has("content")) {
                recoveredSections.put(item)
            }
        }
        require(recoveredSections.length() > 0) {
            "The on-device model did not return recoverable structured sections."
        }
        return JSONObject()
            .put("sections", recoveredSections)
            .put("prescribed_medications", JSONArray())
            .put(
                "coverage_warnings",
                JSONArray().put(
                    "The on-device response was incomplete. Only complete evidence-linked " +
                        "sections were recovered; verify the transcript and medicines manually."
                )
            )
    }

    private fun balancedJsonObjects(raw: String): List<String> = buildList {
        val objectStarts = ArrayDeque<Int>()
        var inString = false
        var escaped = false
        raw.forEachIndexed { index, character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{' -> objectStarts.addLast(index)
                    '}' -> {
                        if (objectStarts.isNotEmpty()) {
                            val objectStart = objectStarts.removeLast()
                            add(raw.substring(objectStart, index + 1))
                        }
                    }
                }
            }
        }
    }
}
