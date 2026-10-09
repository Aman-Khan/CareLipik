package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalNoteGenerationEngine
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationResult
import org.json.JSONArray
import org.json.JSONObject

class DirectGeminiClinicalNoteGenerationEngine(
    apiKey: () -> String?
) : ClinicalNoteGenerationEngine {
    private val client = DirectGeminiClient(apiKey)
    private val parser = HttpGeminiClinicalNoteGenerationEngine("https://unused.invalid")

    override fun generate(request: ClinicalNoteGenerationRequest): ClinicalNoteGenerationResult {
        if (request.reviewedTranscript.isBlank()) {
            return ClinicalNoteGenerationResult.Failure("A reviewed transcript is required.")
        }
        return runCatching {
            val raw = client.generateJson(prompt(request), schema(request))
            val normalized = normalize(raw, request)
            ClinicalNoteGenerationResult.Success(
                parser.parseDraftResponse(normalized.toString(), request)
            )
        }.getOrElse { error ->
            ClinicalNoteGenerationResult.Failure(
                error.message?.takeIf(String::isNotBlank)
                    ?: "Gemini clinical note generation could not be completed."
            )
        }
    }

    private fun prompt(request: ClinicalNoteGenerationRequest): String {
        val sections = request.noteFormat.sectionDefinitions.joinToString { "${it.id} (${it.title})" }
        return """Create an unverified doctor-review draft using only facts explicitly stated in the consultation transcript. Do not diagnose, prescribe, infer findings, or fill missing information. Questions are not patient findings. Preserve negation and historical context. Return every required section, using an empty string if unsupported. Prescribed medications may include only a medicine explicitly prescribed or recommended by the Doctor in this consultation, never existing medicines or pharmacy suggestions. Output language: ${request.outputLanguage.displayName}. Format: ${request.noteFormat.displayName}. Required sections: $sections. Specialty: ${request.specialtyName.ifBlank { "not provided" }}. Patient age: ${request.patientAge.ifBlank { "not provided" }}. Visit reason: ${request.visitReason.ifBlank { "not provided" }}.\n\nReviewed transcript:\n${request.reviewedTranscript}"""
    }

    private fun schema(request: ClinicalNoteGenerationRequest) = JSONObject()
        .put("type", "OBJECT")
        .put("properties", JSONObject()
            .put("sections", JSONObject().put("type", "ARRAY").put(
                "items", JSONObject().put("type", "OBJECT")
                    .put("properties", JSONObject()
                        .put("id", JSONObject().put("type", "STRING").put(
                            "enum", JSONArray(request.noteFormat.sectionDefinitions.map { it.id })
                        ))
                        .put("content", JSONObject().put("type", "STRING"))
                        .put("source_turn_ids", JSONObject().put("type", "ARRAY").put(
                            "items", JSONObject().put("type", "STRING")
                        )))
                    .put("required", JSONArray(listOf("id", "content", "source_turn_ids")))
            ))
            .put("prescribed_medications", JSONObject().put("type", "ARRAY").put(
                "items", JSONObject().put("type", "OBJECT")
                    .put("properties", JSONObject().apply {
                        listOf(
                            "name", "generic_name", "strength", "dose", "route",
                            "frequency", "duration", "instructions"
                        ).forEach { put(it, JSONObject().put("type", "STRING")) }
                        put("source_turn_ids", JSONObject().put("type", "ARRAY").put(
                            "items", JSONObject().put("type", "STRING")
                        ))
                    })
                    .put("required", JSONArray(listOf(
                        "name", "generic_name", "strength", "dose", "route", "frequency",
                        "duration", "instructions", "source_turn_ids"
                    )))
            )))
        .put("required", JSONArray(listOf("sections", "prescribed_medications")))

    private fun normalize(
        raw: JSONObject,
        request: ClinicalNoteGenerationRequest
    ): JSONObject {
        val definitions = request.noteFormat.sectionDefinitions.associateBy { it.id }
        val rawSections = raw.optJSONArray("sections") ?: JSONArray()
        val sections = JSONArray()
        for (index in 0 until rawSections.length()) {
            val section = rawSections.optJSONObject(index) ?: continue
            val definition = definitions[section.optString("id")] ?: continue
            sections.put(JSONObject(section.toString()).put("title", definition.title))
        }
        val rawMedications = raw.optJSONArray("prescribed_medications") ?: JSONArray()
        val medications = JSONArray()
        for (index in 0 until rawMedications.length()) {
            val medication = rawMedications.optJSONObject(index) ?: continue
            val evidence = medication.optJSONArray("source_turn_ids")?.let { ids ->
                buildList {
                    for (idIndex in 0 until ids.length()) ids.optString(idIndex)
                        .takeIf(String::isNotBlank)?.let(::add)
                }.joinToString()
            }.orEmpty()
            medications.put(JSONObject(medication.toString()).put("source_evidence", evidence))
        }
        return JSONObject()
            .put("sections", sections)
            .put("prescribed_medications", medications)
            .put("coverage_warnings", JSONArray())
    }
}
