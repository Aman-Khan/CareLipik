package com.carelipik.app.data.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferDeviceKeyTranscriptionGatewayTest {
    @Test
    fun deviceKeyUsesDirectGateway() {
        val direct = StubGateway(RemoteTranscriptionResult.Failure("direct"))
        val fallback = StubGateway(RemoteTranscriptionResult.Failure("fallback"))
        val gateway = PreferDeviceKeyTranscriptionGateway({ true }, direct, fallback)

        val result = gateway.transcribe(request()) as RemoteTranscriptionResult.Failure

        assertEquals("direct", result.message)
        assertEquals(1, direct.calls)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun missingDeviceKeyUsesBackendFallback() {
        val direct = StubGateway(RemoteTranscriptionResult.Failure("direct"))
        val fallback = StubGateway(RemoteTranscriptionResult.Failure("fallback"))
        val gateway = PreferDeviceKeyTranscriptionGateway({ false }, direct, fallback)

        val result = gateway.transcribe(request()) as RemoteTranscriptionResult.Failure

        assertEquals("fallback", result.message)
        assertEquals(0, direct.calls)
        assertEquals(1, fallback.calls)
    }

    private fun request() = RemoteTranscriptionRequest(
        audioPath = "/synthetic.wav",
        model = "saaras:v3",
        languageCode = "en-IN",
        mode = RemoteTranscriptionMode.Transcribe,
        expectedSpeakerCount = 2
    )

    private class StubGateway(
        private val result: RemoteTranscriptionResult
    ) : RemoteTranscriptionGateway {
        var calls = 0
        override fun transcribe(request: RemoteTranscriptionRequest): RemoteTranscriptionResult {
            calls += 1
            return result
        }
    }
}
