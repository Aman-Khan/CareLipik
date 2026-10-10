package com.carelipik.app.ui.screens.transcript

import com.carelipik.app.domain.transcription.AudioTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.SpeakerRole
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewResult
import com.carelipik.app.domain.transcription.TranscriptConcern
import com.carelipik.app.domain.transcription.TranscriptConcernType
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatch
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptViewModelTest {
    @Test
    fun assignedRoles_updateDisplayedTranscriptAndPreserveRolesWhenEdited() {
        val model = TranscriptViewModel(processAsynchronously = false)
        model.setTranscript("Speaker 1: Synthetic hello\nSpeaker 2: Synthetic reply\nSpeaker 3: Synthetic comment")
        model.assignSpeakerRole("speaker-1", SpeakerRole.Doctor)
        model.assignSpeakerRole("speaker-2", SpeakerRole.Patient)
        model.assignSpeakerRole("speaker-3", SpeakerRole.Other)
        assertEquals("Doctor: Synthetic hello\nPatient: Synthetic reply\nOther (Person 3): Synthetic comment",
            model.uiState.value.labelProjection.text)
        model.setDisplayedTranscript(model.uiState.value.labelProjection.text.replace("reply", "edited reply"))
        assertEquals(listOf("speaker-1", "speaker-2", "speaker-3"), model.uiState.value.speakerIds)
        assertEquals(SpeakerRole.Patient, model.uiState.value.speakerRoles["speaker-2"])
        assertTrue(model.transcriptText().contains("Patient: Synthetic edited reply"))
    }

    @Test
    fun enhancementException_clearsLoaderAndKeepsTranscript() {
        lateinit var model: TranscriptViewModel
        model = TranscriptViewModel(processAsynchronously = false,
            onlineReviewAnalyzer = OnlineTranscriptReviewAnalyzer { _, _ ->
                assertTrue(model.uiState.value.isAnalyzingTerms)
                assertFalse(model.uiState.value.canContinue)
                throw IllegalStateException("Synthetic connection failure")
            })
        model.setTranscript("Synthetic consultation")
        model.setOnlineAnalysisConsent(true)
        model.analyzeTermsOnline()
        assertFalse(model.uiState.value.isAnalyzingTerms)
        assertEquals("Synthetic consultation", model.uiState.value.transcript)
        assertTrue(model.uiState.value.clinicalAnalysisWarning!!.contains("Synthetic connection failure"))
    }
    @Test
    fun missingThirdSpeaker_exposesIncompleteSeparationWarning() {
        val viewModel = TranscriptViewModel(engineResolver = resolver(StubEngine(
            TranscriptionResult.Success("Synthetic conversation", segments = listOf(
                TranscriptSegment("speaker-1", "Synthetic first turn"),
                TranscriptSegment("speaker-2", "Synthetic second turn")
            ))
        )), processAsynchronously = false)
        viewModel.transcribe("/private/test.wav", speakerCount = 3)
        assertTrue(viewModel.uiState.value.speakerSeparationWarning!!.contains("selected 3"))
        assertTrue(viewModel.uiState.value.speakerSeparationWarning!!.contains("2 speaker groups"))
    }

    @Test
    fun selectedSpeakerCount_isRetainedWhenRetrying() {
        val counts = mutableListOf<Int>()
        val engine = StubEngine(TranscriptionResult.Success("Synthetic transcript"))
        val configuredResolver = object : TranscriptionEngineResolver {
            override fun resolve(option: TranscriptionEngineOption): AudioTranscriptionEngine = engine
            override fun resolve(option: TranscriptionEngineOption, speakerCount: Int): AudioTranscriptionEngine {
                counts += speakerCount
                return engine
            }
        }
        val viewModel = TranscriptViewModel(engineResolver = configuredResolver, processAsynchronously = false)
        viewModel.transcribe("/private/test.wav", speakerCount = 4)
        viewModel.retry()
        assertEquals(listOf(4, 4), counts)
    }

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

    @Test
    fun onlineEngine_waitsForConsentBeforeUsingCloudCandidates() {
        val cloudConcern = TranscriptConcern(
            id = "cloud:MEDICATION:7:dolo 650",
            text = "Dolo 650",
            startIndex = 7,
            endIndexExclusive = 15,
            type = TranscriptConcernType.MedicalTerm,
            reason = "AI medication candidate; doctor confirmation required."
        )
        var analysisRequests = 0
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(
                StubEngine(
                    result = TranscriptionResult.Success("I take Dolo 650."),
                    option = TranscriptionEngineOption.SaarasHindiHinglish
                )
            ),
            onlineReviewAnalyzer = OnlineTranscriptReviewAnalyzer { _, _ ->
                analysisRequests += 1
                OnlineTranscriptReviewResult.Success(
                    concerns = listOf(cloudConcern),
                    sourceName = "Gemini",
                    codesVerified = false
                )
            },
            processAsynchronously = false
        )

        viewModel.transcribe(
            "/private/recording.wav",
            TranscriptionLanguage.English,
            TranscriptionEngineOption.SaarasHindiHinglish
        )

        assertTrue(viewModel.uiState.value.concerns.isEmpty())
        assertEquals(0, analysisRequests)
        viewModel.analyzeTermsOnline()
        assertEquals(0, analysisRequests)
        assertEquals(
            "Confirm consent before sending the reviewed transcript for online analysis.",
            viewModel.uiState.value.clinicalAnalysisWarning
        )

        viewModel.setOnlineAnalysisConsent(true)
        viewModel.analyzeTermsOnline()

        assertEquals(1, analysisRequests)
        assertEquals(listOf(cloudConcern), viewModel.uiState.value.concerns)
        assertEquals("Gemini", viewModel.uiState.value.clinicalAnalysisSource)
        assertFalse(viewModel.uiState.value.canContinue)
        viewModel.confirmConcern(cloudConcern.id)
        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun offlineEngine_canUseGeminiAndMergesLocalRecognitionCorrections() {
        val transcript = "I have cuff and take Dolo 650."
        val medicineStart = transcript.indexOf("Dolo 650")
        val cloudConcern = TranscriptConcern(
            id = "cloud:MEDICATION:$medicineStart:dolo 650",
            text = "Dolo 650",
            startIndex = medicineStart,
            endIndexExclusive = medicineStart + "Dolo 650".length,
            type = TranscriptConcernType.MedicalTerm,
            reason = "AI medication candidate; doctor confirmation required."
        )
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(StubEngine(TranscriptionResult.Success(transcript))),
            onlineReviewAnalyzer = OnlineTranscriptReviewAnalyzer { _, _ ->
                OnlineTranscriptReviewResult.Success(
                    concerns = listOf(cloudConcern),
                    sourceName = "Gemini",
                    codesVerified = false
                )
            },
            processAsynchronously = false
        )

        viewModel.transcribe(
            "/private/recording.wav",
            TranscriptionLanguage.English,
            TranscriptionEngineOption.WhisperMultilingual
        )
        viewModel.setOnlineAnalysisConsent(true)
        viewModel.analyzeTermsOnline()

        assertEquals(listOf("cuff", "Dolo 650"), viewModel.uiState.value.concerns.map { it.text })
        assertEquals(
            "cough",
            viewModel.uiState.value.concerns.first { it.text == "cuff" }.suggestedReplacement
        )
        assertEquals("Gemini", viewModel.uiState.value.clinicalAnalysisSource)
    }

    @Test
    fun failedOnlineEnhancement_keepsOfflineReviewSuggestions() {
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(
                StubEngine(TranscriptionResult.Success("I have cuff for two days."))
            ),
            onlineReviewAnalyzer = OnlineTranscriptReviewAnalyzer { _, _ ->
                OnlineTranscriptReviewResult.Failure("Gemini quota was exhausted.")
            },
            processAsynchronously = false
        )

        viewModel.transcribe(
            "/private/recording.wav",
            TranscriptionLanguage.English,
            TranscriptionEngineOption.WhisperMultilingual
        )
        val offlineConcerns = viewModel.uiState.value.concerns
        viewModel.setOnlineAnalysisConsent(true)
        viewModel.analyzeTermsOnline()

        assertEquals(offlineConcerns, viewModel.uiState.value.concerns)
        assertTrue(
            viewModel.uiState.value.clinicalAnalysisWarning
                ?.contains("Gemini quota was exhausted") == true
        )
    }

    @Test
    fun transcriptEdit_removesStaleOnlineCandidatesAndRequestsReanalysis() {
        val transcript = "Patient takes Dolo 650."
        val medicineStart = transcript.indexOf("Dolo 650")
        val viewModel = TranscriptViewModel(
            engineResolver = resolver(StubEngine(TranscriptionResult.Success(transcript))),
            onlineReviewAnalyzer = OnlineTranscriptReviewAnalyzer { _, _ ->
                OnlineTranscriptReviewResult.Success(
                    concerns = listOf(
                        TranscriptConcern(
                            id = "cloud:MEDICATION:$medicineStart:dolo 650",
                            text = "Dolo 650",
                            startIndex = medicineStart,
                            endIndexExclusive = medicineStart + "Dolo 650".length,
                            type = TranscriptConcernType.MedicalTerm,
                            reason = "AI medication candidate; doctor confirmation required."
                        )
                    ),
                    sourceName = "Gemini",
                    codesVerified = false
                )
            },
            processAsynchronously = false
        )

        viewModel.transcribe(
            "/private/recording.wav",
            TranscriptionLanguage.English,
            TranscriptionEngineOption.WhisperMultilingual
        )
        viewModel.setOnlineAnalysisConsent(true)
        viewModel.analyzeTermsOnline()
        viewModel.setTranscript("Patient takes paracetamol 500 mg.")

        assertEquals(null, viewModel.uiState.value.clinicalAnalysisSource)
        assertTrue(viewModel.uiState.value.concerns.none { it.text == "Dolo 650" })
        assertTrue(
            viewModel.uiState.value.clinicalAnalysisWarning
                ?.contains("Run online medical term analysis again") == true
        )
    }

    @Test
    fun fourPeople_canAssignAdditionalRolesAndRenameWithoutLosingTurns() {
        val viewModel = TranscriptViewModel(processAsynchronously = false)
        val transcript = (1..4).joinToString("\n\n") { "Speaker $it: Synthetic greeting $it" }
        viewModel.setTranscript(transcript)
        assertFalse(viewModel.validateForContinue())

        viewModel.assignSpeakerRole("speaker-1", SpeakerRole.Doctor)
        viewModel.assignSpeakerRole("speaker-2", SpeakerRole.Patient)
        viewModel.assignSpeakerRole("speaker-3", SpeakerRole.Other)
        viewModel.assignSpeakerRole("speaker-4", SpeakerRole.Other)
        viewModel.setSpeakerName("speaker-3", "Synthetic attendant")

        assertTrue(viewModel.validateForContinue())
        assertEquals(SpeakerRole.Other, viewModel.uiState.value.speakerRoles["speaker-3"])
        assertEquals(SpeakerRole.Other, viewModel.uiState.value.speakerRoles["speaker-4"])
        assertEquals("Other (Person 4)", viewModel.uiState.value.speakerLabel("speaker-4"))
        assertTrue(viewModel.transcriptText().contains("Synthetic attendant (Other): Synthetic greeting 3"))
        assertTrue(viewModel.transcriptText().contains("Other (Person 4): Synthetic greeting 4"))
        assertEquals(transcript, viewModel.uiState.value.transcript)

        viewModel.setTranscript(transcript.replace("greeting 3", "reply 3"))
        assertEquals("Synthetic attendant", viewModel.uiState.value.speakerNames["speaker-3"])
        viewModel.setSpeakerName("speaker-3", "")
        assertEquals("Other (Person 3)", viewModel.uiState.value.speakerLabel("speaker-3"))
        viewModel.resetForNewConsultation()
        assertTrue(viewModel.uiState.value.speakerNames.isEmpty())
    }

    @Test
    fun renamingDoctor_preservesRoleAndIgnoresUnknownSpeaker() {
        val viewModel = TranscriptViewModel(processAsynchronously = false)
        viewModel.setTranscript("Speaker 1: Hello\nSpeaker 2: Welcome")
        viewModel.assignSpeakerRole("speaker-1", SpeakerRole.Doctor)
        viewModel.setSpeakerName("speaker-1", "Synthetic doctor")
        viewModel.setSpeakerName("missing", "Ignored")

        assertTrue(viewModel.transcriptText().startsWith("Synthetic doctor (Doctor):"))
        assertFalse(viewModel.uiState.value.speakerNames.containsKey("missing"))
        viewModel.setTranscript("Speaker 2: Welcome")
        assertFalse(viewModel.uiState.value.speakerNames.containsKey("speaker-1"))
    }

    @Test
    fun assigningDoctor_keepsExplicitOtherRoleForSecondPerson() {
        val viewModel = TranscriptViewModel(processAsynchronously = false)
        viewModel.setTranscript("Speaker 1: Hello\nSpeaker 2: Welcome")
        viewModel.assignSpeakerRole("speaker-2", SpeakerRole.Other)
        viewModel.assignSpeakerRole("speaker-1", SpeakerRole.Doctor)

        assertEquals(SpeakerRole.Other, viewModel.uiState.value.speakerRoles["speaker-2"])
    }

    private class StubEngine(
        private val result: TranscriptionResult,
        override val option: TranscriptionEngineOption =
            TranscriptionEngineOption.WhisperMultilingual
    ) : AudioTranscriptionEngine {
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
