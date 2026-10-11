package com.carelipik.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores the letterhead in AES-GCM encrypted storage excluded from backups. */
class EncryptedLetterheadRepository internal constructor(
    context: Context,
    private val profileFile: File
) : com.carelipik.app.domain.prescription.LetterheadRepository {
    constructor(context: Context) : this(context, File(context.noBackupFilesDir, FILE_NAME))

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        if (!profileFile.isFile) return@withContext null
        runCatching { decrypt(profileFile.readBytes()).toString(Charsets.UTF_8) }.getOrNull()
    }

    override suspend fun save(template: String) = withContext(Dispatchers.IO) {
        val pending = File(profileFile.parentFile, "${profileFile.name}.pending")
        pending.writeBytes(encrypt(com.carelipik.app.domain.prescription.PrescriptionTemplates.validate(template).toByteArray(Charsets.UTF_8)))
        if (!pending.renameTo(profileFile)) {
            pending.copyTo(profileFile, overwrite = true)
            check(pending.delete()) { "Could not finish saving doctor profile." }
        }
    }

    private fun encrypt(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plainText)
    }

    private fun decrypt(cipherText: ByteArray): ByteArray {
        require(cipherText.size > IV_BYTES) { "Invalid encrypted doctor profile." }
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
        const val FILE_NAME = "doctor_letterhead.clp"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "carelipik-doctor-letterhead-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
