package com.carelipik.app.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carelipik.app.domain.repository.ApiProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedApiCredentialRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val credentialDirectory by lazy {
        File(context.cacheDir, "synthetic_api_credentials_test")
    }

    @Before
    fun clearCredentials() {
        credentialDirectory.deleteRecursively()
    }

    @Test
    fun keysRoundTripWithoutPlaintextAtRestAndCanBeDeleted() = runBlocking {
        val repository = EncryptedApiCredentialRepository(context, credentialDirectory)
        val syntheticSarvamKey = "synthetic-sarvam-key-123456"
        val syntheticGeminiKey = "synthetic-gemini-key-654321"

        repository.saveKey(ApiProvider.Sarvam, syntheticSarvamKey)
        repository.saveKey(ApiProvider.Gemini, syntheticGeminiKey)

        assertEquals(syntheticSarvamKey, repository.readKey(ApiProvider.Sarvam))
        assertEquals(syntheticGeminiKey, repository.readKey(ApiProvider.Gemini))
        credentialDirectory.listFiles().orEmpty().forEach { file ->
            val atRest = file.readText(Charsets.ISO_8859_1)
            assertFalse(atRest.contains(syntheticSarvamKey))
            assertFalse(atRest.contains(syntheticGeminiKey))
        }

        repository.deleteKey(ApiProvider.Sarvam)
        assertNull(repository.readKey(ApiProvider.Sarvam))
        assertEquals(syntheticGeminiKey, repository.readKey(ApiProvider.Gemini))
    }
}
