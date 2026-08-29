package com.carelipik.app.ui.screens.transcript

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.SpeakerRole
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatch
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
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

    @Test
    fun structuredSegments_requireRoleMappingAndProduceDoctorPatientTranscript() {
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(
                StubEngine(
                    TranscriptionResult.Success(
                        transcript = "Speaker 1: Good morning\n\nSpeaker 2: Hello doctor",
                        segments = listOf(
                            TranscriptSegment("speaker-1", "Good morning"),
                            TranscriptSegment("speaker-2", "Hello doctor")
                        )
                    )
                )
            ),
            processAsynchronously = false
        )

        viewModel.transcribe("/private/recording.wav")

        assertTrue(viewModel.uiState.value.canShowConversation)
        assertEquals(listOf("speaker-1", "speaker-2"), viewModel.uiState.value.pendingSpeakerIds)
        assertFalse(viewModel.uiState.value.canContinue)

        viewModel.assignSpeakerRole("speaker-1", SpeakerRole.Doctor)
        viewModel.setViewMode(TranscriptViewMode.Conversation)

        assertEquals(SpeakerRole.Patient, viewModel.uiState.value.speakerRoles["speaker-2"])
        assertEquals(TranscriptViewMode.Conversation, viewModel.uiState.value.viewMode)
        assertEquals(
            "Doctor: Good morning\n\nPatient: Hello doctor",
            viewModel.transcriptText()
        )
        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun confidentDoctorVoiceMatch_assignsRolesAndStillAllowsManualCorrection() {
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(
                StubEngine(
                    TranscriptionResult.Success(
                        transcript = "Speaker 1: Hello\n\nSpeaker 2: Good morning",
                        segments = listOf(
                            TranscriptSegment("speaker-1", "Hello"),
                            TranscriptSegment("speaker-2", "Good morning")
                        ),
                        doctorVoiceMatch = DoctorVoiceRoleMatchResult.Matched(
                            DoctorVoiceRoleMatch("speaker-2", 0.8f, 0.84f, 0.2f)
                        )
                    )
                )
            ),
            processAsynchronously = false
        )

        viewModel.transcribe("/private/recording.wav")

        assertEquals(SpeakerRole.Doctor, viewModel.uiState.value.speakerRoles["speaker-2"])
        assertEquals(SpeakerRole.Patient, viewModel.uiState.value.speakerRoles["speaker-1"])
        viewModel.assignSpeakerRole("speaker-1", SpeakerRole.Doctor)
        assertEquals(SpeakerRole.Doctor, viewModel.uiState.value.speakerRoles["speaker-1"])
        assertEquals(SpeakerRole.Patient, viewModel.uiState.value.speakerRoles["speaker-2"])
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
