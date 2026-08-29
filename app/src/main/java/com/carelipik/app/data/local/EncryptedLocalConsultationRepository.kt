package com.carelipik.app.data.local

import android.content.Context
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.repository.ConsultationRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** AES-GCM encrypted, app-private consultation history backed by Android Keystore. */
class EncryptedLocalConsultationRepository(context: Context) : ConsultationRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME)

    override suspend fun list(): List<ApprovedConsultation> = withContext(Dispatchers.IO) {
        directory.listFiles { file -> file.extension == FILE_EXTENSION }.orEmpty()
            .mapNotNull { file ->
                runCatching { decodeFromStorage(decrypt(file.readBytes())) }.getOrNull()
            }
            .sortedByDescending(ApprovedConsultation::approvedAtMillis)
    }

    override suspend fun get(id: String): ApprovedConsultation? = withContext(Dispatchers.IO) {
        val file = fileFor(id)
        if (!file.isFile) {
            null
        } else {
            runCatching { decodeFromStorage(decrypt(file.readBytes())) }.getOrNull()
        }
    }

    override suspend fun save(consultation: ApprovedConsultation) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = fileFor(consultation.id)
        val pending = File(directory, "${target.name}.pending")
        pending.writeBytes(encrypt(encodeForStorage(consultation)))
        if (!pending.renameTo(target)) {
            pending.copyTo(target, overwrite = true)
            check(pending.delete()) { "Could not finish saving consultation history." }
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        fileFor(id).delete()
        File(directory, "${fileFor(id).name}.pending").delete()
        Unit
    }

    private fun fileFor(id: String): File {
        val safeId = runCatching { UUID.fromString(id).toString() }.getOrElse {
            throw IllegalArgumentException("Invalid consultation identifier.")
        }
        return File(directory, "$safeId.$FILE_EXTENSION")
    }

    private fun encrypt(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plainText)
    }

    private fun decrypt(cipherText: ByteArray): ByteArray {
        require(cipherText.size > IV_BYTES) { "Invalid encrypted consultation data." }
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
        return KeyGenerator.getInstance("AES", KEYSTORE).run {
            init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(
                        android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                    ).setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    internal fun encodeForStorage(item: ApprovedConsultation): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeString(item.id)
                output.writeLong(item.approvedAtMillis)
                output.writeString(item.patientName)
                output.writeString(item.patientAge)
                output.writeString(item.visitReason)
                output.writeString(item.draft.patientAge)
                output.writeString(item.draft.presentingComplaint)
                output.writeString(item.draft.history)
                output.writeString(item.draft.keyFindings)
                output.writeString(item.draft.assessmentNotes)
                output.writeString(item.draft.planNotes)
                output.writeString(item.draft.reviewedTranscript)
            }
            bytes.toByteArray()
        }

    internal fun decodeFromStorage(bytes: ByteArray): ApprovedConsultation =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            when (val version = input.readInt()) {
                1 -> input.decodeVersionOne()
                FORMAT_VERSION -> input.decodeCurrentVersion()
                else -> error("Unsupported consultation format $version.")
            }
        }

    private fun DataInputStream.decodeVersionOne(): ApprovedConsultation = ApprovedConsultation(
        id = readUTF(),
        approvedAtMillis = readLong(),
        patientName = readUTF(),
        patientAge = readUTF(),
        visitReason = readUTF(),
        draft = ClinicalDraft(
            presentingComplaint = readUTF(),
            history = readUTF(),
            keyFindings = readUTF(),
            assessmentNotes = readUTF(),
            planNotes = readUTF()
        )
    )

    private fun DataInputStream.decodeCurrentVersion(): ApprovedConsultation =
        ApprovedConsultation(
            id = readString(),
            approvedAtMillis = readLong(),
            patientName = readString(),
            patientAge = readString(),
            visitReason = readString(),
            draft = ClinicalDraft(
                patientAge = readString(),
                presentingComplaint = readString(),
                history = readString(),
                keyFindings = readString(),
                assessmentNotes = readString(),
                planNotes = readString(),
                reviewedTranscript = readString()
            )
        )

    private fun DataOutputStream.writeString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_STORED_STRING_BYTES) { "Consultation text is too large." }
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MAX_STORED_STRING_BYTES) { "Invalid consultation text length." }
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    companion object {
        const val DIRECTORY_NAME = "consultation_history"
        private const val FILE_EXTENSION = "clh"
        private const val FORMAT_VERSION = 2
        private const val MAX_STORED_STRING_BYTES = 4 * 1024 * 1024
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "carelipik-consultation-history-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
