package com.carelipik.app.data.extraction

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class DirectGeminiClient(
    private val apiKey: () -> String?,
    private val model: String = "gemini-2.5-flash"
) {
    fun generateJson(prompt: String, responseSchema: JSONObject): JSONObject {
        val key = apiKey()?.trim().orEmpty()
        require(key.isNotBlank()) {
            "Add a Gemini API key in Doctor profile, or configure the CareLipik backend."
        }
        val payload = JSONObject()
            .put("contents", org.json.JSONArray().put(
                JSONObject().put("role", "user").put(
                    "parts",
                    org.json.JSONArray().put(JSONObject().put("text", prompt))
                )
            ))
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0)
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", responseSchema)
            )
        val connection = (URL("$BASE_URL/models/$model:generateContent").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", key)
            setRequestProperty("Cache-Control", "no-store")
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                error(message.ifBlank { "Gemini returned HTTP $code." })
            }
            val text = JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        const val CONNECT_TIMEOUT = 30_000
        const val READ_TIMEOUT = 90_000
    }
}
