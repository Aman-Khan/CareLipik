package com.carelipik.app.ui.screens.savedrecordings

import com.carelipik.app.domain.model.RecordedAudio
import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.domain.model.RestoredSavedRecording
import com.carelipik.app.domain.model.SavedRecording
import com.carelipik.app.domain.repository.SavedRecordingRepository
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRecordingsViewModelTest {
    private val audio = RecordedAudio("synthetic.wav", 32_044L, durationMillis = 1_000L)

    @Test
    fun save_returnsHomeCallbackOnlyAfterDurableSave() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        var wasSaved = false
        save(viewModel) { wasSaved = repository.recordings.size == 1 }

        assertTrue(wasSaved)
        assertEquals("Synthetic patient", repository.recordings.values.single().patientName)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun saveFailure_keepsRecordingAndShowsRecoverableError() {
        val repository = FakeRepository().apply { failSave = true }
        val viewModel = viewModel(repository)
        var wasSaved = false
        save(viewModel) { wasSaved = true }

        assertFalse(wasSaved)
        assertEquals("Synthetic storage failure", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun missingAudio_doesNotCreateSavedConsultation() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        viewModel.save(null, "Synthetic patient", "", "", TranscriptionLanguage.English,
            TranscriptionEngineOption.MedAsrEnglish, true) {}

        assertTrue(repository.recordings.isEmpty())
        assertTrue(viewModel.uiState.value.error!!.contains("Finish recording"))
    }

    @Test
    fun resumeThenSave_updatesExistingRecordingInsteadOfCreatingDuplicate() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        save(viewModel) {}
        val id = repository.recordings.keys.single()
        viewModel.startNewConsultation()
        viewModel.resume(id) { true }
        save(viewModel) {}

        assertEquals(1, repository.recordings.size)
        assertEquals(id, repository.recordings.keys.single())
    }

    @Test
    fun resumeFailure_doesNotRemoveDurableRecording() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        save(viewModel) {}
        viewModel.startNewConsultation()
        viewModel.resume(repository.recordings.keys.single()) { false }

        assertEquals(1, repository.recordings.size)
        assertTrue(viewModel.uiState.value.error!!.contains("could not be loaded"))
        viewModel.completeConsultation()
        assertEquals(1, repository.recordings.size)
    }

    @Test
    fun approval_removesOnlyTheActiveSavedRecording() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        save(viewModel) {}
        val first = repository.recordings.keys.single()
        viewModel.startNewConsultation()
        save(viewModel) {}
        viewModel.completeConsultation()

        assertEquals(setOf(first), repository.recordings.keys)
    }

    @Test
    fun delete_refreshesSavedRecordingList() {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        save(viewModel) {}
        viewModel.refresh()
        viewModel.delete(viewModel.uiState.value.recordings.single().id)

        assertTrue(viewModel.uiState.value.recordings.isEmpty())
    }

    private fun viewModel(repository: FakeRepository) = SavedRecordingsViewModel(
        repository, processAsynchronously = false, currentTimeMillis = { 1_000L }
    )

    private fun save(viewModel: SavedRecordingsViewModel, onSaved: () -> Unit) {
        viewModel.save(audio, " Synthetic patient ", "30", "Synthetic visit",
            TranscriptionLanguage.Hinglish, TranscriptionEngineOption.SaarasHindiHinglish,
            true, onSaved = onSaved)
    }

    private inner class FakeRepository : SavedRecordingRepository {
        val recordings = linkedMapOf<String, SavedRecording>()
        var failSave = false
        override suspend fun list() = recordings.values.toList()
        override suspend fun save(recording: SavedRecording, audio: RecordedAudio) {
            if (failSave) error("Synthetic storage failure")
            recordings[recording.id] = recording
        }
        override suspend fun restore(id: String) = RestoredSavedRecording(recordings.getValue(id), audio)
        override suspend fun delete(id: String) { recordings.remove(id) }
    }
}
