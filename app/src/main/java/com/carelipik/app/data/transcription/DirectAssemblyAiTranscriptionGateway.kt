package com.carelipik.app.data.transcription

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Calls AssemblyAI directly with a user-supplied, Android Keystore-protected key. */
class DirectAssemblyAiTranscriptionGateway(
    private val apiKey: () -> String?,
    private val pollIntervalMillis: Long = 3_000L,
    private val maxPollAttempts: Int = 200
) : AssemblyAiTranscriptionGateway {
    override fun transcribe(
        request: AssemblyAiTranscriptionRequest
    ): AssemblyAiTranscriptionResult {
        val key = apiKey()?.trim().orEmpty()
        if (key.isBlank()) {
            return AssemblyAiTranscriptionResult.Failure(
                "Add an AssemblyAI API key in Doctor profile."
            )
        }
        val audio = File(request.audioPath)
        if (!audio.isFile || audio.length() == 0L) {
            return AssemblyAiTranscriptionResult.Failure("The recording is unavailable.")
        }
        return runCatching {
            val uploadUrl = upload(audio, key)
            val body = JSONObject()
                .put("audio_url", uploadUrl)
                .put(
                    "speech_models",
                    JSONArray().put(PRIMARY_MODEL).put(FALLBACK_MODEL)
                )
                .put("speaker_labels", true)
                .put("format_text", true)
                .put("punctuate", true)
                .put("language_detection", true)
                .put(
                    "language_detection_options",
                    JSONObject().put("on_no_speech_detected", "fallback")
                )
                .put(
                    "speech_understanding",
                    JSONObject().put(
                        "request",
                        JSONObject().put(
                            "speaker_identification",
                            JSONObject()
                                .put("speaker_type", "name")
                                .put("known_values", JSONArray())
                        )
                    )
                )
            val transcriptId = requestJson("POST", "/v2/transcript", key, body)
                .optString("id")
            require(transcriptId.matches(SAFE_ID)) {
                "AssemblyAI returned an invalid transcript identifier."
            }
            poll(transcriptId, key)
        }.getOrElse { error ->
            AssemblyAiTranscriptionResult.Failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "AssemblyAI transcription could not be completed."
            )
        }
    }

    private fun upload(audio: File, key: String): String {
        val connection = openConnection("POST", "/v2/upload", key).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            setFixedLengthStreamingMode(audio.length())
        }
        val response = try {
            audio.inputStream().use { input -> connection.outputStream.use(input::copyTo) }
            connection.readJson()
        } finally {
            connection.disconnect()
        }
        return response.optString("upload_url").also { url ->
            require(url.startsWith(UPLOAD_URL_PREFIX)) {
                "AssemblyAI returned an invalid upload URL."
            }
        }
    }

    private fun poll(transcriptId: String, key: String): AssemblyAiTranscriptionResult {
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) Thread.sleep(pollIntervalMillis)
            val response = requestJson("GET", "/v2/transcript/$transcriptId", key)
            when (response.optString("status").lowercase()) {
                "queued", "processing" -> Unit
                "error" -> return AssemblyAiTranscriptionResult.Failure(
                    response.optString("error").ifBlank {
                        "AssemblyAI could not process this recording."
                    }
                )
                "completed" -> return parseCompleted(response)
                else -> error("AssemblyAI returned an unknown transcript state.")
            }
        }
        return AssemblyAiTranscriptionResult.Failure(
            "AssemblyAI transcription is taking longer than expected. Try again shortly."
        )
    }

    internal fun parseCompleted(response: JSONObject): AssemblyAiTranscriptionResult.Success {
        val utterances = response.optJSONArray("utterances") ?: JSONArray()
        val identifiedSpeakers = response
            .optJSONObject("speech_understanding")
            ?.optJSONObject("response")
            ?.optJSONObject("speaker_identification")
            ?.takeIf { it.optString("status").equals("success", ignoreCase = true) }
            ?.optJSONObject("mapping")
        val identifiedNames = buildSet {
            identifiedSpeakers?.keys()?.forEach { key ->
                identifiedSpeakers.optString(key).trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val segments = buildList {
            for (index in 0 until utterances.length()) {
                val utterance = utterances.optJSONObject(index) ?: continue
                val providerSpeaker = utterance.optString("speaker").trim()
                val identifiedName = identifiedSpeakers
                    ?.optString(providerSpeaker)
                    ?.trim()
                    .orEmpty()
                val speaker = identifiedName.ifBlank {
                    providerSpeaker.takeIf { it.isNotBlank() }?.let {
                        if (it in identifiedNames) it else "Speaker $it"
                    }.orEmpty()
                }
                val text = utterance.optString("text").trim()
                if (speaker.isNotBlank() && text.isNotBlank()) {
                    add(
                        RemoteSpeakerSegment(
                            speakerId = speaker,
                            transcript = text,
                            startTimeSeconds = utterance.optionalMilliseconds("start"),
                            endTimeSeconds = utterance.optionalMilliseconds("end")
                        )
                    )
                }
            }
        }.sortedBy { it.startTimeSeconds ?: Double.MAX_VALUE }
        return AssemblyAiTranscriptionResult.Success(
            transcript = response.optString("text").trim(),
            segments = segments,
            detectedLanguage = response.optString("language_code").takeIf(String::isNotBlank),
            modelUsed = response.optString("speech_model_used").takeIf(String::isNotBlank)
        )
    }

    private fun requestJson(
        method: String,
        path: String,
        key: String,
        body: JSONObject? = null
    ): JSONObject {
        val connection = openConnection(method, path, key).apply {
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            body?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes ->
                connection.outputStream.use { it.write(bytes) }
            }
            connection.readJson()
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(method: String, path: String, key: String) =
        (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Authorization", key)
            setRequestProperty("Accept", "application/json")
        }

    private fun HttpURLConnection.readJson(): JSONObject {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val providerMessage = runCatching {
                JSONObject(text).optString("error")
            }.getOrNull().orEmpty()
            error(providerMessage.ifBlank { "AssemblyAI returned HTTP $code." })
        }
        return JSONObject(text)
    }

    private fun JSONObject.optionalMilliseconds(name: String): Double? =
        if (has(name) && !isNull(name)) {
            optDouble(name).takeIf { !it.isNaN() }?.div(1_000.0)
        } else {
            null
        }

    private companion object {
        const val BASE_URL = "https://api.assemblyai.com"
        const val UPLOAD_URL_PREFIX = "https://cdn.assemblyai.com/upload/"
        const val PRIMARY_MODEL = "universal-3-5-pro"
        const val FALLBACK_MODEL = "universal-2"
        const val TIMEOUT_MILLIS = 60_000
        val SAFE_ID = Regex("[A-Za-z0-9-]+")
    }
}
