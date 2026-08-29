package com.carelipik.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.repository.DoctorProfileRepository
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores one doctor profile in app-private AES-GCM storage backed by Android Keystore. */
class EncryptedDoctorProfileRepository internal constructor(
    context: Context,
    private val profileFile: File
) : DoctorProfileRepository {
    constructor(context: Context) : this(context, File(context.filesDir, FILE_NAME))

    override suspend fun load(): DoctorProfile? = withContext(Dispatchers.IO) {
        if (!profileFile.isFile) return@withContext null
        runCatching { decode(decrypt(profileFile.readBytes())) }.getOrNull()
    }

    override suspend fun save(profile: DoctorProfile) = withContext(Dispatchers.IO) {
        val pending = File(profileFile.parentFile, "${profileFile.name}.pending")
        pending.writeBytes(encrypt(encode(profile)))
        if (!pending.renameTo(profileFile)) {
            pending.copyTo(profileFile, overwrite = true)
            check(pending.delete()) { "Could not finish saving doctor profile." }
        }
    }

    internal fun encode(profile: DoctorProfile): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(FORMAT_VERSION)
            output.writeUTF(profile.fullName)
            output.writeUTF(profile.specialty)
            output.writeUTF(profile.registrationNumber)
            output.writeUTF(profile.clinicName)
            output.writeInt(profile.preferredLanguages.size)
            profile.preferredLanguages.sortedBy { it.name }.forEach { output.writeUTF(it.name) }
            output.writeUTF(profile.processingPreference.name)
        }
        bytes.toByteArray()
    }

    internal fun decode(bytes: ByteArray): DoctorProfile = DataInputStream(
        ByteArrayInputStream(bytes)
    ).use { input ->
        require(input.readInt() == FORMAT_VERSION) { "Unsupported doctor profile format." }
        val fullName = input.readUTF()
        val specialty = input.readUTF()
        val registrationNumber = input.readUTF()
        val clinicName = input.readUTF()
        val languageCount = input.readInt()
        require(languageCount in 1..MAX_LANGUAGES) { "Invalid preferred languages." }
        val languages = buildSet {
            repeat(languageCount) {
                val language = TranscriptionLanguage.valueOf(input.readUTF())
                require(language != TranscriptionLanguage.Auto) { "Auto is not a saved language." }
                add(language)
            }
        }
        DoctorProfile(
            fullName = fullName,
            specialty = specialty,
            registrationNumber = registrationNumber,
            clinicName = clinicName,
            preferredLanguages = languages,
            processingPreference = ProcessingPreference.valueOf(input.readUTF())
        )
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
        const val FILE_NAME = "doctor_profile.clp"
        const val FORMAT_VERSION = 1
        const val MAX_LANGUAGES = 3
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "carelipik-doctor-profile-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
