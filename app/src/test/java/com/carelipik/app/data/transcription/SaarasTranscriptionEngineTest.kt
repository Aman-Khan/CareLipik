package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaarasTranscriptionEngineTest {
    @Test
    fun hindi_usesTranscribeModeAndTwoSpeakers() {
        val gateway = CapturingGateway(
            RemoteTranscriptionResult.Success("नमस्ते")
        )
        val engine = SaarasTranscriptionEngine(gateway)

        engine.transcribe("/private/test.wav", TranscriptionLanguage.Hindi)

        assertEquals("saaras:v3", gateway.request?.model)
        assertEquals("hi-IN", gateway.request?.languageCode)
        assertEquals(RemoteTranscriptionMode.Transcribe, gateway.request?.mode)
        assertEquals(2, gateway.request?.expectedSpeakerCount)
    }

    @Test
    fun hinglish_usesCodeMixMode() {
        val gateway = CapturingGateway(
            RemoteTranscriptionResult.Success("मुझे cough है")
        )
        val engine = SaarasTranscriptionEngine(gateway)

        engine.transcribe("/private/test.wav", TranscriptionLanguage.Hinglish)

        assertEquals(RemoteTranscriptionMode.CodeMix, gateway.request?.mode)
    }

    @Test
    fun diarizedSegments_areNumberedWithoutGuessingRoles() {
        val gateway = CapturingGateway(
            RemoteTranscriptionResult.Success(
                transcript = "Fallback",
                segments = listOf(
                    RemoteSpeakerSegment("speaker-b", "नमस्ते"),
                    RemoteSpeakerSegment("speaker-a", "मुझे तीन दिन से खांसी है"),
                    RemoteSpeakerSegment("speaker-b", "क्या बुखार भी है?")
                )
            )
        )
        val engine = SaarasTranscriptionEngine(gateway)

        val result = engine.transcribe("/private/test.wav", TranscriptionLanguage.Hindi)

        assertEquals(
            TranscriptionResult.Success(
                "Speaker 1: नमस्ते\n\n" +
                    "Speaker 2: मुझे तीन दिन से खांसी है\n\n" +
                    "Speaker 1: क्या बुखार भी है?"
            ),
            result
        )
    }

    @Test
    fun english_isRejectedBeforeUpload() {
        val gateway = CapturingGateway(RemoteTranscriptionResult.Success("unused"))
        val engine = SaarasTranscriptionEngine(gateway)

        val result = engine.transcribe("/private/test.wav", TranscriptionLanguage.English)

        assertTrue(result is TranscriptionResult.Failure)
        assertEquals(null, gateway.request)
    }

    @Test
    fun gatewayFailure_isReturnedForReview() {
        val engine = SaarasTranscriptionEngine(
            CapturingGateway(RemoteTranscriptionResult.Failure("Service unavailable"))
        )

        val result = engine.transcribe("/private/test.wav", TranscriptionLanguage.Hindi)

        assertEquals(TranscriptionResult.Failure("Service unavailable"), result)
    }

    private class CapturingGateway(
        private val result: RemoteTranscriptionResult
    ) : RemoteTranscriptionGateway {
        var request: RemoteTranscriptionRequest? = null

        override fun transcribe(request: RemoteTranscriptionRequest): RemoteTranscriptionResult {
            this.request = request
            return result
        }
    }
}
