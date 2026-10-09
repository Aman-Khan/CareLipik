package com.carelipik.app.domain.repository

import com.carelipik.app.domain.model.RecordedAudio
import com.carelipik.app.domain.model.RestoredSavedRecording
import com.carelipik.app.domain.model.SavedRecording

interface SavedRecordingRepository {
    suspend fun list(): List<SavedRecording>
    suspend fun save(recording: SavedRecording, audio: RecordedAudio)
    /** Restores an authenticated temporary WAV; the recorder takes ownership of this file. */
    suspend fun restore(id: String): RestoredSavedRecording
    suspend fun delete(id: String)
}
