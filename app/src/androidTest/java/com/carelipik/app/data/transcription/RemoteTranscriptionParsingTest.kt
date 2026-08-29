package com.carelipik.app.data.transcription

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteTranscriptionParsingTest {
    private val gateway = HttpRemoteTranscriptionGateway("")

    @Test
    fun jsonNullSpeakerWarning_isParsedAsAbsent() {
        val result = gateway.parseCompletedResponse(
            """{
                "status":"completed",
                "transcript":"Speaker 1: Hello",
                "segments":[],
                "speaker_separation_warning":null
            }""".trimIndent()
        )

        assertNull(result.speakerSeparationWarning)
    }

    @Test
    fun realSpeakerWarning_isPreserved() {
        val result = gateway.parseCompletedResponse(
            """{
                "status":"completed",
                "transcript":"Continuous transcript",
                "segments":[],
                "speaker_separation_warning":"Review speaker separation."
            }""".trimIndent()
        )

        assertEquals("Review speaker separation.", result.speakerSeparationWarning)
    }
}
