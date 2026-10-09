package com.carelipik.app.data.local

import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.domain.model.SavedRecording
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

internal object SavedRecordingCodec {
    fun encode(recording: SavedRecording): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(1)
            output.writeUTF(recording.id)
            output.writeLong(recording.savedAtMillis)
            output.writeUTF(recording.patientName)
            output.writeUTF(recording.patientAge)
            output.writeUTF(recording.visitReason)
            output.writeUTF(recording.language.name)
            output.writeUTF(recording.engine.name)
            output.writeUTF(recording.audioDisplayName)
            output.writeLong(recording.durationMillis)
            output.writeUTF(recording.audioSource.name)
            output.writeBoolean(recording.hasRecordingConsent)
        }
        bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): SavedRecording = DataInputStream(ByteArrayInputStream(bytes)).use {
        require(it.readInt() == 1) { "Unsupported saved recording format." }
        SavedRecording(
            id = it.readUTF(),
            savedAtMillis = it.readLong(),
            patientName = it.readUTF(),
            patientAge = it.readUTF(),
            visitReason = it.readUTF(),
            language = TranscriptionLanguage.valueOf(it.readUTF()),
            engine = TranscriptionEngineOption.valueOf(it.readUTF()),
            audioDisplayName = it.readUTF(),
            durationMillis = it.readLong(),
            audioSource = RecordedAudioSource.valueOf(it.readUTF()),
            hasRecordingConsent = it.readBoolean()
        ).also { recording ->
            require(UUID.fromString(recording.id).toString() == recording.id)
            require(recording.patientName.isNotBlank() && recording.durationMillis > 0)
            require(recording.engine.supports(recording.language))
            require(recording.hasRecordingConsent)
        }
    }
}
