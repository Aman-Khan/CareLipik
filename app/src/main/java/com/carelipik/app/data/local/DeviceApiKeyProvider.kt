package com.carelipik.app.data.local

import android.content.Context
import com.carelipik.app.domain.repository.ApiProvider
import kotlinx.coroutines.runBlocking

/** Reads a credential only at request time; callers must never retain or log the returned value. */
class DeviceApiKeyProvider(context: Context) {
    private val repository = EncryptedApiCredentialRepository(context.applicationContext)

    fun get(provider: ApiProvider): String? = runBlocking {
        repository.readKey(provider)
    }
}
