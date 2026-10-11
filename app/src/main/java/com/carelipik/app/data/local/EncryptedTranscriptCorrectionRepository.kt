package com.carelipik.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.carelipik.app.domain.repository.TranscriptCorrectionRepository
import com.carelipik.app.domain.training.TranscriptCorrectionLabel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Encrypted, app-private correction examples. Nothing is uploaded by this repository. */
class EncryptedTranscriptCorrectionRepository(context: Context) : TranscriptCorrectionRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME)
    private val labelsDirectory = File(directory, "labels")
    private val audioDirectory = File(directory, "audio")

    override fun save(
        label: TranscriptCorrectionLabel,
        sourceAudioPath: String
    ) {
        require(label.confirmedByDoctor) { "Only doctor-confirmed corrections may be stored." }
        val id = UUID.fromString(label.id).toString()
        val audioId = UUID.fromString(label.audioClipReference.substringAfterLast(':')).toString()
        check(labelsDirectory.mkdirs() || labelsDirectory.isDirectory)
        check(audioDirectory.mkdirs() || audioDirectory.isDirectory)

        val source = File(sourceAudioPath)
        require(source.isFile) { "Source audio is unavailable." }
        val audioTarget = File(audioDirectory, "$audioId.audio")
        if (!audioTarget.isFile) writeEncryptedFileAtomically(audioTarget, source)
        writeAtomically(File(labelsDirectory, "$id.label"), encrypt(encode(label)))
    }

    override suspend fun list(): List<TranscriptCorrectionLabel> = withContext(Dispatchers.IO) {
        labelsDirectory.listFiles { file -> file.extension == "label" }.orEmpty()
            .mapNotNull { runCatching { decode(decrypt(it.readBytes())) }.getOrNull() }
            .sortedByDescending(TranscriptCorrectionLabel::createdAtMillis)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        directory.deleteRecursively()
        Unit
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val pending = File(target.parentFile, "${target.name}.pending")
        pending.writeBytes(bytes)
        if (!pending.renameTo(target)) {
            pending.copyTo(target, overwrite = true)
            check(pending.delete()) { "Could not finish storing training data." }
        }
    }

    private fun writeEncryptedFileAtomically(target: File, source: File) {
        val pending = File(target.parentFile, "${target.name}.pending")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        pending.outputStream().buffered().use { output ->
            output.write(cipher.iv)
            CipherOutputStream(output, cipher).use { encryptedOutput ->
                source.inputStream().buffered().use { input -> input.copyTo(encryptedOutput) }
            }
        }
        if (!pending.renameTo(target)) {
            pending.copyTo(target, overwrite = true)
            check(pending.delete()) { "Could not finish storing encrypted source audio." }
        }
    }

    private fun encode(label: TranscriptCorrectionLabel): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(label.schemaVersion)
                output.writeUTF(label.id)
                output.writeLong(label.createdAtMillis)
                output.writeUTF(label.audioClipReference)
                output.writeUTF(label.asrText)
                output.writeUTF(label.correctedText)
                output.writeUTF(label.originalTerm)
                output.writeUTF(label.correctedTerm)
                output.writeUTF(label.language)
                output.writeUTF(label.transcriptionEngine)
                output.writeBoolean(label.confirmedByDoctor)
            }
            bytes.toByteArray()
        }

    private fun decode(bytes: ByteArray): TranscriptCorrectionLabel =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt()
            require(version == FORMAT_VERSION) { "Unsupported correction format." }
            TranscriptCorrectionLabel(
                id = input.readUTF(),
                createdAtMillis = input.readLong(),
                audioClipReference = input.readUTF(),
                asrText = input.readUTF(),
                correctedText = input.readUTF(),
                originalTerm = input.readUTF(),
                correctedTerm = input.readUTF(),
                language = input.readUTF(),
                transcriptionEngine = input.readUTF(),
                confirmedByDoctor = input.readBoolean(),
                schemaVersion = version
            )
        }

    private fun encrypt(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plainText)
    }

    private fun decrypt(cipherText: ByteArray): ByteArray {
        require(cipherText.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, cipherText.copyOfRange(0, IV_BYTES))
        )
        return cipher.doFinal(cipherText.copyOfRange(IV_BYTES, cipherText.size))
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "transcript_training_data"
        const val FORMAT_VERSION = 1
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "carelipik-transcript-corrections-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
