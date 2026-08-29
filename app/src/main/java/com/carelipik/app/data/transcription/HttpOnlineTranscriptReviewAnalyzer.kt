package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.OnlineTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewResult
import com.carelipik.app.domain.transcription.TranscriptConcern
import com.carelipik.app.domain.transcription.TranscriptConcernType
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Calls CareLipik's backend without storing a provider key or patient transcript on device logs. */
class HttpOnlineTranscriptReviewAnalyzer(
    backendBaseUrl: String,
    private val allowInsecureLocalhost: Boolean = false
) : OnlineTranscriptReviewAnalyzer {
    private val baseUrl = backendBaseUrl.trim().trimEnd('/')

    override fun analyze(
        transcript: String,
        language: TranscriptionLanguage
    ): OnlineTranscriptReviewResult {
        if (transcript.isBlank()) return OnlineTranscriptReviewResult.Success(
            concerns = emptyList(),
            sourceName = "Gemini",
            codesVerified = false
        )
        val parsedBaseUrl = runCatching { URL(baseUrl) }.getOrNull()
        val isSecure = parsedBaseUrl?.protocol == "https"
        val isAllowedDebugLocalhost = allowInsecureLocalhost &&
            parsedBaseUrl?.protocol == "http" &&
            parsedBaseUrl.host in LOCAL_HOSTS
        if (!isSecure && !isAllowedDebugLocalhost) {
            return OnlineTranscriptReviewResult.Failure(
                "The clinical analysis backend must use HTTPS."
            )
        }

        return runCatching {
            val connection = (URL("$baseUrl/v1/clinical-entities").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Cache-Control", "no-store")
            }
            try {
                val requestBody = JSONObject()
                    .put("transcript", transcript)
                    .put("language_code", language.toBackendLanguageCode())
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(requestBody) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = runCatching { JSONObject(body).optString("message") }
                        .getOrDefault("")
                    return@runCatching OnlineTranscriptReviewResult.Failure(
                        message.ifBlank { "Online clinical term analysis returned HTTP $code." }
                    )
                }
                JSONObject(body).toReviewResult(transcript)
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            OnlineTranscriptReviewResult.Failure(
                "Online clinical term analysis could not be completed."
            )
        }
    }

    private fun JSONObject.toReviewResult(transcript: String): OnlineTranscriptReviewResult {
        val entities = optJSONArray("entities")
        val concerns = buildList {
            if (entities != null) {
                for (index in 0 until entities.length()) {
                    val entity = entities.optJSONObject(index) ?: continue
                    val start = entity.optInt("start_index", -1)
                    val end = entity.optInt("end_index_exclusive", -1)
                    val sourceText = entity.optString("source_text")
                    if (start < 0 || end !in (start + 1)..transcript.length) continue
                    if (transcript.substring(start, end) != sourceText) continue
                    val possibleAsrError = entity.optBoolean("possible_asr_error", false)
                    val normalized = entity.optString("normalized_text").trim()
                    val genericSalt = entity.optString("generic_salt").trim()
                    val strength = entity.optString("strength").trim()
                    val rawConfidence = entity.optDouble("confidence", 0.0)
                    val confidence = if (rawConfidence.isFinite()) {
                        rawConfidence.coerceIn(0.0, 1.0)
                    } else {
                        0.0
                    }
                    val category = entity.optString("category", "MEDICAL_TERM")
                    val assertion = entity.optString("assertion", "PRESENT")
                    add(
                        TranscriptConcern(
                            id = entity.optString("id").ifBlank {
                                "cloud:$category:$start:${sourceText.lowercase()}"
                            },
                            text = sourceText,
                            startIndex = start,
                            endIndexExclusive = end,
                            type = if (possibleAsrError) {
                                TranscriptConcernType.PossibleRecognitionError
                            } else {
                                TranscriptConcernType.MedicalTerm
                            },
                            reason = buildReason(
                                category = category,
                                assertion = assertion,
                                confidence = confidence,
                                sourceText = sourceText,
                                normalizedText = normalized,
                                genericSalt = genericSalt,
                                strength = strength
                            ),
                            suggestedReplacement = normalized.takeIf {
                                possibleAsrError && !it.equals(sourceText, ignoreCase = true)
                            }
                        )
                    )
                }
            }
        }
        return OnlineTranscriptReviewResult.Success(
            concerns = concerns,
            sourceName = optString("source", "Gemini").replaceFirstChar(Char::uppercase),
            codesVerified = optBoolean("codes_verified", false)
        )
    }

    private fun buildReason(
        category: String,
        assertion: String,
        confidence: Double,
        sourceText: String,
        normalizedText: String,
        genericSalt: String,
        strength: String
    ): String {
        val confidencePercent = (confidence * 100).toInt()
        val normalization = buildList {
            genericSalt.takeIf { it.isNotBlank() }?.let { add("generic salt: $it") }
            strength.takeIf { it.isNotBlank() }?.let { add("strength: $it") }
            normalizedText.takeIf {
                it.isNotBlank() && !it.equals(sourceText, ignoreCase = true) && genericSalt.isBlank()
            }?.let { add("normalized term: $it") }
        }.joinToString()
        val normalizationText = if (normalization.isBlank()) "" else " Suggested $normalization."
        return "AI candidate: ${category.replace('_', ' ').lowercase()}, " +
            "${assertion.replace('_', ' ').lowercase()} ($confidencePercent% confidence)." +
            normalizationText +
            " The doctor must confirm it; terminology codes are not yet verified."
    }

    private fun TranscriptionLanguage.toBackendLanguageCode(): String = when (this) {
        TranscriptionLanguage.English -> "en-IN"
        TranscriptionLanguage.Hindi -> "hi-IN"
        TranscriptionLanguage.Hinglish -> "hi-Latn-IN"
        TranscriptionLanguage.Auto -> "en-IN"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 90_000
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost")
    }
}
