package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveMultilingualTranscriptionEngineTest {
    @Test
    fun online_usesHostedEngine() {
        val online = StubEngine("online")
        val offline = StubEngine("offline")
        val engine = AdaptiveMultilingualTranscriptionEngine({ true }, online, offline)

        assertEquals(TranscriptionResult.Success("online"), engine.transcribe("audio", TranscriptionLanguage.Auto))
        assertEquals(1, online.calls)
        assertEquals(0, offline.calls)
    }

    @Test
    fun offline_usesLocalTurboEngine() {
        val online = StubEngine("online")
        val offline = StubEngine("offline")
        val engine = AdaptiveMultilingualTranscriptionEngine({ false }, online, offline)

        assertEquals(TranscriptionResult.Success("offline"), engine.transcribe("audio", TranscriptionLanguage.Hinglish))
        assertEquals(0, online.calls)
        assertEquals(1, offline.calls)
    }

    @Test
    fun hostedFailure_fallsBackToLocalTurboEngine() {
        val online = StubEngine(result = TranscriptionResult.Failure("unavailable"))
        val offline = StubEngine("offline")
        val engine = AdaptiveMultilingualTranscriptionEngine({ true }, online, offline)

        assertEquals(TranscriptionResult.Success("offline"), engine.transcribe("audio", TranscriptionLanguage.English))
        assertEquals(1, offline.calls)
    }

    private class StubEngine(
        text: String = "",
        private val result: TranscriptionResult = TranscriptionResult.Success(text)
    ) : AudioTranscriptionEngine {
        override val option = TranscriptionEngineOption.AssemblyAiUniversal
        var calls = 0

        override fun transcribe(audioPath: String, language: TranscriptionLanguage): TranscriptionResult {
            calls += 1
            return result
        }
    }
}
