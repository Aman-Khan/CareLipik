package com.carelipik.app.data.transcription

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Calls Sarvam Batch directly with a user-supplied, Keystore-protected key. */
class DirectSarvamTranscriptionGateway(
    private val apiKey: () -> String?,
    private val pollIntervalMillis: Long = 2_000L,
    private val maxPollAttempts: Int = 150
) : RemoteTranscriptionGateway {
    override fun transcribe(request: RemoteTranscriptionRequest): RemoteTranscriptionResult {
        val key = apiKey()?.trim().orEmpty()
        if (key.isBlank()) return RemoteTranscriptionResult.Failure(
            "Add a Sarvam API key in Doctor profile, or configure the CareLipik backend."
        )
        val audio = File(request.audioPath)
        if (!audio.isFile || audio.length() == 0L) {
            return RemoteTranscriptionResult.Failure("The recording is unavailable.")
        }
        return runCatching {
            val job = postJson(
                path = "/speech-to-text/job/v1",
                key = key,
                body = JSONObject().put(
                    "job_parameters",
                    JSONObject()
                        .put("model", request.model)
                        .put("language_code", request.languageCode)
                        .put("mode", request.mode.wireValue)
                        .put("with_diarization", true)
                        .put("num_speakers", request.expectedSpeakerCount)
                )
            ).optString("job_id")
            require(job.matches(SAFE_ID)) { "Sarvam returned an invalid job identifier." }
            val upload = postJson(
                "/speech-to-text/job/v1/upload-files",
                key,
                JSONObject().put("job_id", job).put("files", JSONArray().put(audio.name))
            )
            val uploadUrl = upload.optJSONObject("upload_urls")
                ?.optJSONObject(audio.name)?.optString("file_url").orEmpty()
            require(uploadUrl.startsWith("https://")) { "Sarvam returned an invalid upload URL." }
            uploadAudio(uploadUrl, audio)
            requestJson("POST", "/speech-to-text/job/v1/$job/start", key)
            poll(job, key)
        }.getOrElse { error ->
            RemoteTranscriptionResult.Failure(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not complete Saaras transcription."
            )
        }
    }

    private fun poll(jobId: String, key: String): RemoteTranscriptionResult {
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) Thread.sleep(pollIntervalMillis)
            val status = requestJson("GET", "/speech-to-text/job/v1/$jobId/status", key)
            when (status.optString("job_state").lowercase()) {
                "accepted", "pending", "running" -> Unit
                "failed" -> return RemoteTranscriptionResult.Failure(
                    status.optString("error_message").ifBlank { "Sarvam could not process this recording." }
                )
                "completed", "partiallycompleted" -> {
                    val outputName = successfulOutputName(status)
                    val download = postJson(
                        "/speech-to-text/job/v1/download-files",
                        key,
                        JSONObject().put("job_id", jobId)
                            .put("files", JSONArray().put(outputName))
                    )
                    val downloadUrl = download.optJSONObject("download_urls")
                        ?.optJSONObject(outputName)?.optString("file_url").orEmpty()
                    require(downloadUrl.startsWith("https://")) {
                        "Sarvam returned an invalid result URL."
                    }
                    return parseProviderResult(requestAbsoluteJson(downloadUrl))
                }
                else -> error("Sarvam returned an unknown job state.")
            }
        }
        return RemoteTranscriptionResult.Failure(
            "Saaras transcription is taking longer than expected. Try again shortly."
        )
    }

    private fun parseProviderResult(json: JSONObject): RemoteTranscriptionResult.Success {
        val entries = json.optJSONObject("diarized_transcript")?.optJSONArray("entries")
        val segments = buildList {
            if (entries != null) for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val text = entry.optString("transcript").trim()
                val speaker = entry.optString("speaker_id").trim()
                if (text.isNotBlank() && speaker.isNotBlank()) {
                    add(
                        RemoteSpeakerSegment(
                            speakerId = speaker,
                            transcript = text,
                            startTimeSeconds = entry.optionalDouble("start_time_seconds"),
                            endTimeSeconds = entry.optionalDouble("end_time_seconds")
                        )
                    )
                }
            }
        }.sortedBy { it.startTimeSeconds ?: Double.MAX_VALUE }
        val reliable = segments.map(RemoteSpeakerSegment::speakerId).toSet().size == 2
        return RemoteTranscriptionResult.Success(
            transcript = json.optString("transcript").trim(),
            segments = if (reliable) segments else emptyList(),
            speakerSeparationWarning = if (reliable) null else {
                "Saaras could not reliably separate exactly two voices. Review speaker roles."
            }
        )
    }

    private fun successfulOutputName(status: JSONObject): String {
        val details = status.optJSONArray("job_details") ?: JSONArray()
        for (index in 0 until details.length()) {
            val detail = details.optJSONObject(index) ?: continue
            if (!detail.optString("state").equals("success", ignoreCase = true)) continue
            val outputs = detail.optJSONArray("outputs") ?: continue
            val name = outputs.optJSONObject(0)?.optString("file_name").orEmpty()
            if (name.isNotBlank()) return name
        }
        error("Sarvam completed without a transcription output.")
    }

    private fun postJson(path: String, key: String, body: JSONObject) =
        requestJson("POST", path, key, body)

    private fun requestJson(
        method: String,
        path: String,
        key: String,
        body: JSONObject? = null
    ): JSONObject {
        val connection = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("Accept", "application/json")
            setRequestProperty("api-subscription-key", key)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return connection.useJson(body)
    }

    private fun requestAbsoluteJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("Accept", "application/json")
        }
        return connection.useJson(null)
    }

    private fun uploadAudio(url: String, audio: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            doOutput = true
            setRequestProperty("Content-Type", "audio/wav")
            setRequestProperty("x-ms-blob-type", "BlockBlob")
        }
        try {
            audio.inputStream().use { input -> connection.outputStream.use(input::copyTo) }
            check(connection.responseCode in 200..299) { "Sarvam audio upload failed." }
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.useJson(body: JSONObject?): JSONObject = try {
        body?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes ->
            outputStream.use { it.write(bytes) }
        }
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val providerMessage = runCatching {
                JSONObject(text).optJSONObject("error")?.optString("message")
                    ?: JSONObject(text).optString("message")
            }.getOrNull().orEmpty()
            error(providerMessage.ifBlank { "Sarvam returned HTTP $code." })
        }
        JSONObject(text)
    } finally {
        disconnect()
    }

    private fun JSONObject.optionalDouble(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name).takeIf { !it.isNaN() } else null

    private companion object {
        const val BASE_URL = "https://api.sarvam.ai"
        const val TIMEOUT = 60_000
        val SAFE_ID = Regex("[A-Za-z0-9._-]+")
    }
}
