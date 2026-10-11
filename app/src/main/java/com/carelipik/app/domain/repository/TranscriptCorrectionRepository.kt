package com.carelipik.app.domain.repository

import com.carelipik.app.domain.training.TranscriptCorrectionLabel

interface TranscriptCorrectionRepository {
    fun save(label: TranscriptCorrectionLabel, sourceAudioPath: String)
    suspend fun list(): List<TranscriptCorrectionLabel>
    suspend fun deleteAll()
}
