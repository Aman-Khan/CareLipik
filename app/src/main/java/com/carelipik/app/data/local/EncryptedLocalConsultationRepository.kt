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
            .mapNotNull { file -> runCatching { decode(decrypt(file.readBytes())) }.getOrNull() }
            .sortedByDescending(ApprovedConsultation::approvedAtMillis)
    }

    override suspend fun get(id: String): ApprovedConsultation? = withContext(Dispatchers.IO) {
        val file = fileFor(id)
        if (!file.isFile) null else runCatching { decode(decrypt(file.readBytes())) }.getOrNull()
    }

    override suspend fun save(consultation: ApprovedConsultation) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val target = fileFor(consultation.id)
        val pending = File(directory, "${target.name}.pending")
        pending.writeBytes(encrypt(encode(consultation)))
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

    private fun encode(item: ApprovedConsultation): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeUTF(item.id)
                output.writeLong(item.approvedAtMillis)
                output.writeUTF(item.patientName)
                output.writeUTF(item.patientAge)
                output.writeUTF(item.visitReason)
                output.writeUTF(item.draft.presentingComplaint)
                output.writeUTF(item.draft.history)
                output.writeUTF(item.draft.keyFindings)
                output.writeUTF(item.draft.assessmentNotes)
                output.writeUTF(item.draft.planNotes)
            }
            bytes.toByteArray()
        }

    private fun decode(bytes: ByteArray): ApprovedConsultation =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == FORMAT_VERSION) { "Unsupported consultation format." }
            ApprovedConsultation(
                id = input.readUTF(),
                approvedAtMillis = input.readLong(),
                patientName = input.readUTF(),
                patientAge = input.readUTF(),
                visitReason = input.readUTF(),
                draft = ClinicalDraft(
                    presentingComplaint = input.readUTF(),
                    history = input.readUTF(),
                    keyFindings = input.readUTF(),
                    assessmentNotes = input.readUTF(),
                    planNotes = input.readUTF()
                )
            )
        }

    companion object {
        const val DIRECTORY_NAME = "consultation_history"
        private const val FILE_EXTENSION = "clh"
        private const val FORMAT_VERSION = 1
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "carelipik-consultation-history-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
