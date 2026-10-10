package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalNoteGenerationEngine
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationResult
import com.carelipik.app.domain.extraction.ClinicalVisitReasonFormatter
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.ClinicalNoteSection
import com.carelipik.app.domain.model.MedicationDraft
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Calls CareLipik's backend; the Gemini credential is never placed in the Android app. */
class HttpGeminiClinicalNoteGenerationEngine(
    backendBaseUrl: String,
    private val allowInsecureLocalhost: Boolean = false
) : ClinicalNoteGenerationEngine {
    private val baseUrl = backendBaseUrl.trim().trimEnd('/')

    override fun generate(
        request: ClinicalNoteGenerationRequest
    ): ClinicalNoteGenerationResult {
        if (request.reviewedTranscript.isBlank()) {
            return ClinicalNoteGenerationResult.Failure("A reviewed transcript is required.")
        }
        if (!isBackendUrlAllowed()) {
            return ClinicalNoteGenerationResult.Failure(
                "The clinical note backend must use HTTPS."
            )
        }
        return runCatching { execute(request) }.getOrElse {
            ClinicalNoteGenerationResult.Failure(
                "Gemini clinical note generation could not be completed."
            )
        }
    }

    private fun execute(request: ClinicalNoteGenerationRequest): ClinicalNoteGenerationResult {
        val connection = (URL("$baseUrl/v1/clinical-note-drafts").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Cache-Control", "no-store")
        }
        return try {
            val requestBody = JSONObject()
                .put("transcript", request.reviewedTranscript)
                .put("language_code", request.sourceLanguage.backendLanguageCode())
                .put("note_format", request.noteFormat.name)
                .put("output_language", request.outputLanguage.name)
                .put("specialty_name", request.specialtyName)
                .put("patient_age", request.patientAge)
                .put("visit_reason", request.visitReason)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(requestBody) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).safeString("message") }
                    .getOrDefault("")
                ClinicalNoteGenerationResult.Failure(
                    message.ifBlank { "Clinical note generation returned HTTP $code." }
                )
            } else {
                ClinicalNoteGenerationResult.Success(parseDraftResponse(body, request))
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseDraftResponse(
        body: String,
        request: ClinicalNoteGenerationRequest,
        generationSource: ClinicalNoteGenerationSource = ClinicalNoteGenerationSource.Gemini
    ): ClinicalDraft = JSONObject(body).toDraft(request, generationSource)

    private fun JSONObject.toDraft(
        request: ClinicalNoteGenerationRequest,
        generationSource: ClinicalNoteGenerationSource
    ): ClinicalDraft {
        val rawSections = optJSONArray("sections") ?: JSONArray()
        val sectionsById = buildMap {
            for (index in 0 until rawSections.length()) {
                val item = rawSections.optJSONObject(index) ?: continue
                val id = item.safeString("id")
                if (id.isBlank() || containsKey(id)) continue
                put(id, item)
            }
        }
        val sections = request.noteFormat.sectionDefinitions.map { definition ->
            val item = sectionsById[definition.id]
            ClinicalNoteSection(
                id = definition.id,
                title = item?.safeString("title")?.ifBlank { definition.title }
                    ?: definition.title,
                content = item?.safeString("content").orEmpty(),
                sourceTurnIds = item?.optJSONArray("source_turn_ids").turnIds()
            )
        }
        val medications = buildList {
            val rawMedications = optJSONArray("prescribed_medications") ?: JSONArray()
            for (index in 0 until minOf(rawMedications.length(), MAX_MEDICATIONS)) {
                val item = rawMedications.optJSONObject(index) ?: continue
                val name = item.safeString("name")
                if (name.isBlank()) continue
                add(
                    MedicationDraft(
                        name = name,
                        genericName = item.safeString("generic_name"),
                        strength = item.safeString("strength"),
                        dose = item.safeString("dose"),
                        route = item.safeString("route"),
                        frequency = item.safeString("frequency"),
                        duration = item.safeString("duration"),
                        instructions = item.safeString("instructions"),
                        sourceEvidence = item.safeString("source_evidence"),
                        isDoctorReviewed = false
                    )
                )
            }
        }
        return ClinicalDraft(
            patientAge = request.patientAge,
            presentingComplaint = ClinicalVisitReasonFormatter.concise(
                safeString("visit_reason").ifBlank { request.visitReason }
            ),
            reviewedTranscript = request.reviewedTranscript,
            noteFormat = request.noteFormat,
            noteLanguage = request.outputLanguage,
            specialtyName = request.specialtyName,
            structuredSections = sections,
            medications = medications,
            coverageWarnings = optJSONArray("coverage_warnings").strings(MAX_WARNINGS),
            generationSource = generationSource
        )
    }

    private fun isBackendUrlAllowed(): Boolean {
        val parsed = runCatching { URL(baseUrl) }.getOrNull() ?: return false
        return parsed.protocol == "https" || (
            allowInsecureLocalhost && parsed.protocol == "http" && parsed.host in LOCAL_HOSTS
        )
    }

    private fun JSONObject.safeString(name: String): String =
        if (isNull(name)) "" else optString(name, "").trim()

    private fun JSONArray?.turnIds(): List<String> = strings(MAX_SOURCE_TURNS)
        .filter { TURN_ID.matches(it) }
        .distinct()

    private fun JSONArray?.strings(maximum: Int): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(length(), maximum)) {
                val value = optString(index, "").trim()
                if (value.isNotBlank() && value != "null") add(value)
            }
        }
    }

    private fun TranscriptionLanguage.backendLanguageCode(): String = when (this) {
        TranscriptionLanguage.Auto -> "en-IN"
        TranscriptionLanguage.English -> "en-IN"
        TranscriptionLanguage.Hindi -> "hi-IN"
        TranscriptionLanguage.Hinglish -> "hi-Latn-IN"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 90_000
        const val MAX_MEDICATIONS = 100
        const val MAX_WARNINGS = 512
        const val MAX_SOURCE_TURNS = 512
        val TURN_ID = Regex("T[1-9]\\d*")
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "10.0.2.2")
    }
}
