package com.carelipik.app.data.transcription

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssemblyAiResponseParsingTest {
    @Test
    fun completedProviderResponse_parsesUtterancesAndMilliseconds() {
        val response = JSONObject()
            .put("status", "completed")
            .put("text", "Please sit down. Thank you.")
            .put("language_code", "en")
            .put("speech_model_used", "universal-3-5-pro")
            .put(
                "speech_understanding",
                JSONObject()
                    .put(
                        "response",
                        JSONObject().put(
                            "speaker_identification",
                            JSONObject()
                                .put("status", "success")
                                .put(
                                    "mapping",
                                    JSONObject()
                                        .put("A", "Dr. Vikram")
                                        .put("B", "Arjun")
                                )
                        )
                    )
            )
            .put(
                "utterances",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("speaker", "A")
                            .put("text", "Please sit down.")
                            .put("start", 1_250)
                            .put("end", 2_500)
                    )
                    .put(
                        JSONObject()
                            .put("speaker", "Arjun")
                            .put("text", "Thank you.")
                            .put("start", 2_800)
                            .put("end", 3_400)
                    )
            )

        val result = DirectAssemblyAiTranscriptionGateway(apiKey = { "synthetic-test-key" })
            .parseCompleted(response)

        assertEquals("en", result.detectedLanguage)
        assertEquals("universal-3-5-pro", result.modelUsed)
        assertEquals(
            listOf(
                RemoteSpeakerSegment("Dr. Vikram", "Please sit down.", 1.25, 2.5),
                RemoteSpeakerSegment("Arjun", "Thank you.", 2.8, 3.4)
            ),
            result.segments
        )
    }
}
