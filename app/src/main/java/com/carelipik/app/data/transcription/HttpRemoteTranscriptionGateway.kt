package com.carelipik.app.data.transcription

import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Uploads a recording to the CareLipik backend and polls its provider-neutral batch job.
 * This client never holds or sends a Sarvam API key.
 */
class HttpRemoteTranscriptionGateway(
    backendBaseUrl: String,
    private val allowInsecureLocalhost: Boolean = false,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
    private val maxPollAttempts: Int = DEFAULT_MAX_POLL_ATTEMPTS
) : RemoteTranscriptionGateway {
    private val baseUrl = backendBaseUrl.trim().trimEnd('/')

    override fun transcribe(request: RemoteTranscriptionRequest): RemoteTranscriptionResult {
        if (baseUrl.isBlank()) {
            return RemoteTranscriptionResult.Failure(
                "Online Hindi/Hinglish transcription is not configured yet. " +
                    "Configure the secure CareLipik transcription backend or choose Whisper offline."
            )
        }
        val parsedBaseUrl = runCatching { URL(baseUrl) }.getOrNull()
        val isSecure = parsedBaseUrl?.protocol == "https"
        val isAllowedDebugLocalhost = allowInsecureLocalhost &&
            parsedBaseUrl?.protocol == "http" &&
            parsedBaseUrl.host in LOCAL_HOSTS
        if (!isSecure && !isAllowedDebugLocalhost) {
            return RemoteTranscriptionResult.Failure(
                "The transcription backend must use HTTPS. " +
                    "Plain HTTP is allowed only for debug localhost testing."
            )
        }
        val audioFile = File(request.audioPath)
        if (!audioFile.isFile || audioFile.length() == 0L) {
            return RemoteTranscriptionResult.Failure("The recording is unavailable.")
        }

        return runCatching {
            when (val submission = submit(audioFile, request)) {
                is JobSubmission.Completed -> submission.result
                is JobSubmission.Accepted -> poll(submission.jobId)
            }
        }.getOrElse { error ->
            val message = if (error is BackendException) {
                error.message
            } else {
                null
            }
            RemoteTranscriptionResult.Failure(
                message ?: "Online transcription could not be completed. " +
                    "Check the connection and try again."
            )
        }
    }

    private fun submit(
        audioFile: File,
        request: RemoteTranscriptionRequest
    ): JobSubmission {
        val boundary = "CareLipik-${UUID.randomUUID()}"
        val connection = openConnection("$baseUrl/v1/transcriptions", "POST").apply {
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            doOutput = true
        }
        try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeFormField(boundary, "provider", "sarvam")
                output.writeFormField(boundary, "model", request.model)
                output.writeFormField(boundary, "language_code", request.languageCode)
                output.writeFormField(boundary, "mode", request.mode.wireValue)
                output.writeFormField(boundary, "with_diarization", "true")
                output.writeFormField(
                    boundary,
                    "num_speakers",
                    request.expectedSpeakerCount.toString()
                )
                output.writeAudioFile(boundary, audioFile)
                output.writeBytes("--$boundary--\r\n")
            }
            val response = connection.readResponse()
            if (response.code !in 200..299) throw BackendException(response.errorMessage())
            val json = JSONObject(response.body)
            if (json.optString("status").equals("completed", ignoreCase = true)) {
                return JobSubmission.Completed(json.toRemoteResult())
            }
            val jobId = json.optString("id").ifBlank { json.optString("job_id") }
            require(jobId.matches(SAFE_JOB_ID)) { "Backend returned an invalid job identifier." }
            return JobSubmission.Accepted(jobId)
        } finally {
            connection.disconnect()
        }
    }

    private fun poll(jobId: String): RemoteTranscriptionResult {
        repeat(maxPollAttempts) { attempt ->
            if (attempt > 0) Thread.sleep(pollIntervalMillis)
            val connection = openConnection("$baseUrl/v1/transcriptions/$jobId", "GET")
            try {
                val response = connection.readResponse()
                if (response.code !in 200..299) throw BackendException(response.errorMessage())
                val json = JSONObject(response.body)
                when (json.optString("status").lowercase()) {
                    "completed" -> return json.toRemoteResult()
                    "failed" -> return RemoteTranscriptionResult.Failure(
                        json.optString("message").ifBlank {
                            "The online transcription service could not process this recording."
                        }
                    )
                    "queued", "processing", "running" -> Unit
                    else -> throw BackendException("Backend returned an unknown job status.")
                }
            } finally {
                connection.disconnect()
            }
        }
        return RemoteTranscriptionResult.Failure(
            "Online transcription is taking longer than expected. Try again shortly."
        )
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
        }
    }

    private fun HttpURLConnection.readResponse(): HttpResponse {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResponse(code, body)
    }

    private fun HttpResponse.errorMessage(): String {
        val backendMessage = runCatching {
            val json = JSONObject(body)
            json.optString("message").ifBlank {
                json.optJSONObject("error")?.optString("message").orEmpty()
            }
        }.getOrDefault("")
        return backendMessage.ifBlank { "Transcription backend returned HTTP $code." }
    }

    private fun JSONObject.toRemoteResult(): RemoteTranscriptionResult.Success {
        val segmentsJson = optJSONArray("segments")
            ?: optJSONObject("diarized_transcript")?.optJSONArray("entries")
        val segments = buildList {
            if (segmentsJson != null) {
                for (index in 0 until segmentsJson.length()) {
                    val item = segmentsJson.optJSONObject(index) ?: continue
                    val text = item.optString("transcript").ifBlank { item.optString("text") }
                    if (text.isNotBlank()) {
                        add(
                            RemoteSpeakerSegment(
                                speakerId = item.optString("speaker_id", "unknown"),
                                transcript = text
                            )
                        )
                    }
                }
            }
        }
        return RemoteTranscriptionResult.Success(
            transcript = optString("transcript"),
            segments = segments
        )
    }

    private fun DataOutputStream.writeFormField(
        boundary: String,
        name: String,
        value: String
    ) {
        writeBytes("--$boundary\r\n")
        writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        write(value.toByteArray(Charsets.UTF_8))
        writeBytes("\r\n")
    }

    private fun DataOutputStream.writeAudioFile(boundary: String, audioFile: File) {
        writeBytes("--$boundary\r\n")
        writeBytes(
            "Content-Disposition: form-data; name=\"file\"; " +
                "filename=\"consultation.wav\"\r\n"
        )
        writeBytes("Content-Type: audio/wav\r\n\r\n")
        audioFile.inputStream().buffered().use { input -> input.copyTo(this) }
        writeBytes("\r\n")
    }

    private sealed interface JobSubmission {
        data class Accepted(val jobId: String) : JobSubmission
        data class Completed(val result: RemoteTranscriptionResult.Success) : JobSubmission
    }

    private data class HttpResponse(val code: Int, val body: String)

    private class BackendException(message: String) : Exception(message)

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val DEFAULT_POLL_INTERVAL_MILLIS = 2_000L
        const val DEFAULT_MAX_POLL_ATTEMPTS = 150
        val SAFE_JOB_ID = Regex("[A-Za-z0-9._-]+")
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost")
    }
}
