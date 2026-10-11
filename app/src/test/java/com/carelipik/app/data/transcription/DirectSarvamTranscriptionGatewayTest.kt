package com.carelipik.app.data.transcription

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DirectSarvamTranscriptionGatewayTest {
    private fun response(count: Int): JSONObject = JSONObject().put("transcript", "Reviewed speech")
        .put("diarized_transcript", JSONObject().put("entries", org.json.JSONArray().apply {
            for (index in count downTo 1) put(JSONObject().put("speaker_id", "voice$index")
                .put("transcript", "Speech $index").put("start_time_seconds", index.toDouble())
                .put("end_time_seconds", index + 0.5))
        }))

    @Test fun maskedOption_preservesOneThroughFourDetectedSpeakersAndTiming() {
        val gateway = DirectSarvamTranscriptionGateway({ null }, preserveDetectedSpeakers = true)
        for (count in 1..4) {
            val result = gateway.parseProviderResult(response(count))
            assertEquals(count, result.segments.size)
            assertEquals("voice1", result.segments.first().speakerId)
            assertEquals(1.0, result.segments.first().startTimeSeconds!!, 0.0)
            assertNull(result.speakerSeparationWarning)
        }
    }
    @Test fun regularOption_keepsExistingTwoSpeakerBehavior() {
        val gateway = DirectSarvamTranscriptionGateway({ null })
        assertEquals(2, gateway.parseProviderResult(response(2)).segments.size)
        assertTrue(gateway.parseProviderResult(response(3)).segments.isEmpty())
    }
    @Test fun missingDiarization_returnsWarningWithoutInventingSpeakers() {
        val result = DirectSarvamTranscriptionGateway({ null }, preserveDetectedSpeakers = true)
            .parseProviderResult(response(0))
        assertTrue(result.segments.isEmpty())
        assertNotNull(result.speakerSeparationWarning)
    }
}
