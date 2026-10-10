package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssemblyAiTranscriptionEngineTest {
    @Test
    fun identifiedSpeakerNames_arePreservedWithTimestamps() {
        val gateway = CapturingGateway(
            AssemblyAiTranscriptionResult.Success(
                transcript = "Fallback transcript",
                segments = listOf(
                    RemoteSpeakerSegment("Dr. Vikram", "Please describe the pain.", 1.2, 2.8),
                    RemoteSpeakerSegment("Arjun", "My ankle hurts.", 3.0, 4.2),
                    RemoteSpeakerSegment("Sunita", "The wrap is ready.", 4.5, 5.7),
                    RemoteSpeakerSegment("Dr. Vikram", "Thank you.", 6.0, 6.7)
                )
            )
        )
        val engine = AssemblyAiTranscriptionEngine(gateway)

        val result = engine.transcribe("/private/synthetic.wav", TranscriptionLanguage.English)

        assertEquals(
            TranscriptionResult.Success(
                transcript = "Dr. Vikram: Please describe the pain.\n\n" +
                    "Arjun: My ankle hurts.\n\n" +
                    "Sunita: The wrap is ready.\n\n" +
                    "Dr. Vikram: Thank you.",
                segments = listOf(
                    TranscriptSegment("Dr. Vikram", "Please describe the pain.", 1.2, 2.8),
                    TranscriptSegment("Arjun", "My ankle hurts.", 3.0, 4.2),
                    TranscriptSegment("Sunita", "The wrap is ready.", 4.5, 5.7),
                    TranscriptSegment("Dr. Vikram", "Thank you.", 6.0, 6.7)
                ),
                speakerSeparationWarning = "AssemblyAI inferred speaker names from conversation " +
                    "context. Confirm every name and clinical role before continuing."
            ),
            result
        )
    }

    @Test
    fun languageAndWholeRecording_arePassedToGateway() {
        val gateway = CapturingGateway(
            AssemblyAiTranscriptionResult.Success("नमस्ते", emptyList())
        )

        AssemblyAiTranscriptionEngine(gateway)
            .transcribe("/private/synthetic.wav", TranscriptionLanguage.Hinglish)

        assertEquals(
            AssemblyAiTranscriptionRequest(
                "/private/synthetic.wav",
                TranscriptionLanguage.Hinglish
            ),
            gateway.request
        )
    }

    @Test
    fun gatewayFailure_isReturnedWithoutFallbackText() {
        val engine = AssemblyAiTranscriptionEngine(
            CapturingGateway(AssemblyAiTranscriptionResult.Failure("Quota exceeded"))
        )

        val result = engine.transcribe("/private/synthetic.wav", TranscriptionLanguage.English)

        assertTrue(result is TranscriptionResult.Failure)
        assertEquals(TranscriptionResult.Failure("Quota exceeded"), result)
    }

    private class CapturingGateway(
        private val result: AssemblyAiTranscriptionResult
    ) : AssemblyAiTranscriptionGateway {
        var request: AssemblyAiTranscriptionRequest? = null

        override fun transcribe(
            request: AssemblyAiTranscriptionRequest
        ): AssemblyAiTranscriptionResult {
            this.request = request
            return result
        }
    }
}
