package com.carelipik.app.data.transcription

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpRemoteTranscriptionGatewayTest {
    @Test
    fun missingBackendUrl_returnsConfigurationFailureWithoutUploading() {
        val gateway = HttpRemoteTranscriptionGateway("")

        val result = gateway.transcribe(testRequest())

        assertTrue(
            (result as RemoteTranscriptionResult.Failure).message.contains("not configured")
        )
    }

    @Test
    fun insecureBackendUrl_isRejectedWithoutUploading() {
        val gateway = HttpRemoteTranscriptionGateway("http://example.test")

        val result = gateway.transcribe(testRequest())

        assertTrue(
            (result as RemoteTranscriptionResult.Failure).message.contains("HTTPS")
        )
    }

    @Test
    fun debugLocalhost_isAllowedBeforeAudioValidation() {
        val gateway = HttpRemoteTranscriptionGateway(
            backendBaseUrl = "http://127.0.0.1:8787",
            allowInsecureLocalhost = true
        )

        val result = gateway.transcribe(testRequest())

        assertTrue(
            (result as RemoteTranscriptionResult.Failure).message.contains("unavailable")
        )
    }

    @Test
    fun completedResponse_doesNotRenderJsonNullAsWarningText() {
        val result = normalizeOptionalString(
            fieldPresent = true,
            isJsonNull = true,
            value = "null"
        )

        assertNull(result)
    }

    @Test
    fun completedResponse_keepsRealSpeakerWarning() {
        val result = normalizeOptionalString(
            fieldPresent = true,
            isJsonNull = false,
            value = "Review speaker separation."
        )

        assertEquals("Review speaker separation.", result)
    }

    private fun testRequest() = RemoteTranscriptionRequest(
        audioPath = "/private/test.wav",
        model = "saaras:v3",
        languageCode = "hi-IN",
        mode = RemoteTranscriptionMode.Transcribe,
        expectedSpeakerCount = 2
    )
}
