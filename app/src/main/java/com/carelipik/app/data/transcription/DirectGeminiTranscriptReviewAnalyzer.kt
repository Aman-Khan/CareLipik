package com.carelipik.app.data.transcription

import com.carelipik.app.data.extraction.DirectGeminiClient
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.json.JSONArray
import org.json.JSONObject

class DirectGeminiTranscriptReviewAnalyzer(
    apiKey: () -> String?
) : OnlineTranscriptReviewAnalyzer {
    private val client = DirectGeminiClient(apiKey)
    private val parser = HttpOnlineTranscriptReviewAnalyzer("https://unused.invalid")

    override fun analyze(
        transcript: String,
        language: TranscriptionLanguage
    ): OnlineTranscriptReviewResult {
        if (transcript.isBlank()) return OnlineTranscriptReviewResult.Success(emptyList(), "Gemini", false)
        return runCatching {
            val raw = client.generateJson(prompt(transcript, language), schema())
            val normalized = normalize(raw, transcript)
            parser.parseResponse(normalized.toString(), transcript)
        }.getOrElse { error ->
            OnlineTranscriptReviewResult.Failure(
                error.message?.takeIf(String::isNotBlank)
                    ?: "Gemini medical-term analysis could not be completed."
            )
        }
    }

    private fun prompt(transcript: String, language: TranscriptionLanguage) =
        """Extract only medical information explicitly written in this ${language.displayName} consultation transcript. Preserve source_text exactly. Include symptoms, diseases, medicine brands, generic salts, allergies, investigations, procedures, anatomy, dosage, strength, route and frequency. Mark assertion as PRESENT, NEGATED, PAST, FAMILY_HISTORY or POSSIBLE. If a word is likely an ASR error, provide a conservative normalized_text and set possible_asr_error true. Do not diagnose, prescribe or invent facts.\n\nTranscript:\n$transcript"""

    private fun schema() = JSONObject()
        .put("type", "OBJECT")
        .put("properties", JSONObject().put(
            "entities",
            JSONObject().put("type", "ARRAY").put(
                "items",
                JSONObject().put("type", "OBJECT")
                    .put("properties", JSONObject()
                        .put("source_text", JSONObject().put("type", "STRING"))
                        .put("normalized_text", JSONObject().put("type", "STRING"))
                        .put("generic_salt", JSONObject().put("type", "STRING"))
                        .put("strength", JSONObject().put("type", "STRING"))
                        .put("category", JSONObject().put("type", "STRING"))
                        .put("assertion", JSONObject().put("type", "STRING"))
                        .put("confidence", JSONObject().put("type", "NUMBER"))
                        .put("possible_asr_error", JSONObject().put("type", "BOOLEAN")))
                    .put("required", JSONArray(listOf(
                        "source_text", "normalized_text", "generic_salt", "strength",
                        "category", "assertion", "confidence", "possible_asr_error"
                    )))
            )
        ))
        .put("required", JSONArray().put("entities"))

    private fun normalize(raw: JSONObject, transcript: String): JSONObject {
        val normalized = JSONArray()
        val occupied = mutableListOf<IntRange>()
        val entities = raw.optJSONArray("entities") ?: JSONArray()
        for (index in 0 until entities.length()) {
            val item = entities.optJSONObject(index) ?: continue
            val source = item.optString("source_text").trim()
            if (source.isBlank()) continue
            val start = transcript.indexOf(source, ignoreCase = true).takeIf { candidate ->
                candidate >= 0 && occupied.none { candidate in it }
            } ?: continue
            val end = start + source.length
            occupied += start until end
            normalized.put(JSONObject(item.toString())
                .put("source_text", transcript.substring(start, end))
                .put("start_index", start)
                .put("end_index_exclusive", end))
        }
        return JSONObject()
            .put("entities", normalized)
            .put("source", "Gemini")
            .put("codes_verified", false)
    }
}
