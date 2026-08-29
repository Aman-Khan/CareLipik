package com.carelipik.app.ui.screens.transcript

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver
import com.carelipik.app.domain.transcription.TranscriptionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptViewModelTest {
    @Test
    fun successfulTranscription_isReadyForReview() {
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(StubEngine(TranscriptionResult.Success("Speaker 1: Hello"))),
            processAsynchronously = false
        )

        viewModel.transcribe("/private/recording.wav")

        assertEquals(TranscriptStatus.Ready, viewModel.uiState.value.status)
        assertEquals("Speaker 1: Hello", viewModel.uiState.value.transcript)
        assertTrue(viewModel.validateForContinue())
    }

    @Test
    fun failedTranscription_exposesRetryMessage() {
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(StubEngine(TranscriptionResult.Failure("Engine unavailable"))),
            processAsynchronously = false
        )

        viewModel.transcribe("/private/recording.wav")

        assertEquals(TranscriptStatus.Error, viewModel.uiState.value.status)
        assertEquals("Engine unavailable", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.canContinue)
    }

    @Test
    fun emptyEditedTranscript_preventsContinue() {
        val viewModel = TranscriptViewModel(processAsynchronously = false)
        viewModel.transcribe("/private/recording.wav")

        viewModel.setTranscript("")

        assertFalse(viewModel.validateForContinue())
        assertEquals(
            "Add or enter a transcript before continuing",
            viewModel.uiState.value.transcriptError
        )
    }

    @Test
    fun selectedLanguage_isForwardedToEngine() {
        val engine = StubEngine(TranscriptionResult.Success("नमस्ते"))
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(engine),
            processAsynchronously = false
        )

        viewModel.transcribe(
            "/private/recording.wav",
            TranscriptionLanguage.Hinglish
        )

        assertEquals(TranscriptionLanguage.Hinglish, engine.receivedLanguage)
    }

    @Test
    fun possibleRecognitionError_blocksContinueUntilDoctorChoosesCorrection() {
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(
                StubEngine(TranscriptionResult.Success("I have had cup for three days."))
            ),
            processAsynchronously = false
        )

        viewModel.transcribe("/private/recording.wav")

        val concern = viewModel.uiState.value.pendingConcerns.single()
        assertEquals("cough", concern.suggestedReplacement)
        assertFalse(viewModel.validateForContinue())
        assertEquals(
            "Confirm or correct every highlighted term before continuing",
            viewModel.uiState.value.transcriptError
        )

        viewModel.applySuggestedReplacement(concern.id)

        assertEquals("I have had cough for three days.", viewModel.transcriptText())
        assertTrue(viewModel.uiState.value.pendingConcerns.isEmpty())
        assertTrue(viewModel.validateForContinue())
    }

    @Test
    fun recognizedMedicalTerm_requiresExplicitDoctorConfirmation() {
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(StubEngine(TranscriptionResult.Success("Mild fever."))),
            processAsynchronously = false
        )

        viewModel.transcribe("/private/recording.wav")

        val concern = viewModel.uiState.value.pendingConcerns.single()
        assertFalse(viewModel.uiState.value.canContinue)
        viewModel.confirmConcern(concern.id)

        assertTrue(viewModel.uiState.value.canContinue)
        assertEquals(1, viewModel.uiState.value.confirmedConcernCount)
    }

    private class StubEngine(
        private val result: TranscriptionResult
    ) : AudioTranscriptionEngine {
        override val option: TranscriptionEngineOption = TranscriptionEngineOption.WhisperMultilingual
        var receivedLanguage: TranscriptionLanguage? = null

        override fun transcribe(
            audioPath: String,
            language: TranscriptionLanguage
        ): TranscriptionResult {
            receivedLanguage = language
            return result
        }
    }

    private fun resolver(engine: AudioTranscriptionEngine): TranscriptionEngineResolver {
        return TranscriptionEngineResolver { engine }
    }
}
