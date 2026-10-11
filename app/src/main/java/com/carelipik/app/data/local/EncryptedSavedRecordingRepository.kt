package com.carelipik.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.carelipik.app.data.audio.WaveAudioInspector
import com.carelipik.app.domain.model.RecordedAudio
import com.carelipik.app.domain.model.RestoredSavedRecording
import com.carelipik.app.domain.model.SavedRecording
import com.carelipik.app.domain.repository.SavedRecordingRepository
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Independently authenticated metadata and audio in one atomically replaced encrypted file. */
class EncryptedSavedRecordingRepository internal constructor(
    context: Context,
    private val directory: File,
    private val restoreDirectory: File
) : SavedRecordingRepository {
    constructor(context: Context) : this(
        context,
        File(context.filesDir, "saved_recordings"),
        File(context.cacheDir, "consultation_audio")
    )

    override suspend fun list(): List<SavedRecording> = withContext(Dispatchers.IO) {
        directory.listFiles { file -> file.extension == "clr" }.orEmpty().map { file ->
            DataInputStream(file.inputStream().buffered()).use { readMetadata(it).first }.also {
                require(fileFor(it.id).name == file.name) { "Invalid saved recording identifier." }
            }
        }.sortedByDescending { it.savedAtMillis }
    }

    override suspend fun save(recording: SavedRecording, audio: RecordedAudio) =
        withContext(Dispatchers.IO) {
            val source = File(audio.localPath)
            val wave = WaveAudioInspector.inspectCompatiblePcm(source)
            require(wave.durationMillis > 0) { "The recording is empty." }
            val metadata = SavedRecordingCodec.encode(recording.copy(durationMillis = wave.durationMillis))
            require(metadata.size <= 262_116) { "Consultation details are too long to save." }
            // Validate before writing; online-processing consent is deliberately not retained.
            SavedRecordingCodec.decode(metadata)
            directory.mkdirs()
            val target = fileFor(recording.id)
            val pending = Files.createTempFile(directory.toPath(), recording.id, ".pending").toFile()
            try {
                val metadataCipher = encryptionCipher()
                val encryptedMetadata = metadataCipher.iv + metadataCipher.doFinal(metadata)
                val audioCipher = encryptionCipher()
                audioCipher.updateAAD(encryptedMetadata)
                DataOutputStream(pending.outputStream().buffered()).use { output ->
                    output.writeInt(1)
                    output.writeInt(encryptedMetadata.size)
                    output.write(encryptedMetadata)
                    output.write(audioCipher.iv)
                    CipherOutputStream(output, audioCipher).use { encryptedAudio ->
                        source.inputStream().buffered().use { it.copyTo(encryptedAudio) }
                    }
                }
                Files.move(
                    pending.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                )
            } finally {
                pending.delete()
            }
            Unit
        }

    override suspend fun restore(id: String): RestoredSavedRecording = withContext(Dispatchers.IO) {
        restoreDirectory.mkdirs()
        val restoredFile = File(restoreDirectory, "restored-${UUID.randomUUID()}.wav")
        try {
            DataInputStream(fileFor(id).inputStream().buffered()).use { input ->
                val (recording, encryptedMetadata) = readMetadata(input)
                require(recording.id == id) { "Invalid saved recording identifier." }
                val iv = ByteArray(12).also(input::readFully)
                val cipher = decryptionCipher(iv).apply { updateAAD(encryptedMetadata) }
                restoredFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        cipher.update(buffer, 0, count)?.let(output::write)
                    }
                    // Explicit doFinal propagates GCM authentication failures before the file is used.
                    output.write(cipher.doFinal())
                }
                val wave = WaveAudioInspector.inspectCompatiblePcm(restoredFile)
                RestoredSavedRecording(
                    recording,
                    RecordedAudio(
                        localPath = restoredFile.absolutePath,
                        sizeBytes = restoredFile.length(),
                        displayName = recording.audioDisplayName,
                        durationMillis = wave.durationMillis,
                        source = recording.audioSource
                    )
                )
            }
        } catch (error: Exception) {
            restoredFile.delete()
            throw error
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val file = fileFor(id)
        check(!file.exists() || file.delete()) { "Could not delete the saved recording." }
    }

    private fun readMetadata(input: DataInputStream): Pair<SavedRecording, ByteArray> {
        require(input.readInt() == 1) { "Unsupported saved recording format." }
        val size = input.readInt()
        require(size in 29..262_144) { "Invalid saved recording metadata." }
        val encrypted = ByteArray(size).also(input::readFully)
        return SavedRecordingCodec.decode(
            decryptionCipher(encrypted.copyOfRange(0, 12)).doFinal(encrypted.copyOfRange(12, size))
        ) to encrypted
    }

    private fun fileFor(id: String): File {
        require(UUID.fromString(id).toString() == id) { "Invalid saved recording identifier." }
        return File(directory, "$id.clr")
    }

    private fun encryptionCipher() = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, secretKey())
    }

    private fun decryptionCipher(iv: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "carelipik-saved-recordings-v1"
    }
}
