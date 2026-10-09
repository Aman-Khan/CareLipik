package com.carelipik.app.domain.repository

enum class ApiProvider {
    Sarvam,
    Gemini
}

interface ApiCredentialRepository {
    suspend fun hasKey(provider: ApiProvider): Boolean
    suspend fun readKey(provider: ApiProvider): String?
    suspend fun saveKey(provider: ApiProvider, apiKey: String)
    suspend fun deleteKey(provider: ApiProvider)
}
