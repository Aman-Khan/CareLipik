package com.carelipik.app.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.carelipik.app.domain.repository.ApiCredentialRepository
import com.carelipik.app.domain.repository.ApiProvider
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stores provider keys as separate app-private AES-GCM blobs backed by Android Keystore. */
class EncryptedApiCredentialRepository internal constructor(
    context: Context,
    private val directory: File
) : ApiCredentialRepository {
    constructor(context: Context) : this(context, File(context.filesDir, DIRECTORY_NAME))

    override suspend fun hasKey(provider: ApiProvider): Boolean = withContext(Dispatchers.IO) {
        readKeyInternal(provider) != null
    }

    override suspend fun readKey(provider: ApiProvider): String? = withContext(Dispatchers.IO) {
        readKeyInternal(provider)
    }

    override suspend fun saveKey(provider: ApiProvider, apiKey: String) = withContext(Dispatchers.IO) {
        val normalized = apiKey.trim()
        require(normalized.length in MIN_KEY_LENGTH..MAX_KEY_LENGTH) { "Invalid API key length." }
        require(normalized.none(Char::isWhitespace)) { "API keys cannot contain spaces." }
        check(directory.exists() || directory.mkdirs()) { "Could not create credential storage." }
        val destination = keyFile(provider)
        val pending = File(directory, "${destination.name}.pending")
        pending.writeBytes(encrypt(normalized.toByteArray(Charsets.UTF_8)))
        if (!pending.renameTo(destination)) {
            pending.copyTo(destination, overwrite = true)
            check(pending.delete()) { "Could not finish saving API key." }
        }
    }

    override suspend fun deleteKey(provider: ApiProvider) = withContext(Dispatchers.IO) {
        val file = keyFile(provider)
        check(!file.exists() || file.delete()) { "Could not delete API key." }
    }

    private fun readKeyInternal(provider: ApiProvider): String? {
        val file = keyFile(provider)
        if (!file.isFile) return null
        return runCatching {
            decrypt(file.readBytes()).toString(Charsets.UTF_8).takeIf(String::isNotBlank)
        }.getOrNull()
    }

    private fun keyFile(provider: ApiProvider) = File(
        directory,
        when (provider) {
            ApiProvider.Sarvam -> "sarvam.key"
            ApiProvider.Gemini -> "gemini.key"
        }
    )

    private fun encrypt(plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher.iv + cipher.doFinal(plainText)
    }

    private fun decrypt(cipherText: ByteArray): ByteArray {
        require(cipherText.size > IV_BYTES) { "Invalid encrypted credential." }
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
        const val DIRECTORY_NAME = "api_credentials"
        const val MIN_KEY_LENGTH = 12
        const val MAX_KEY_LENGTH = 512
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "carelipik-api-credentials-v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
