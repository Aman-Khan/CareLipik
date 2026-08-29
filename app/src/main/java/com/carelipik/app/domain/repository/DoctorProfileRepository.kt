package com.carelipik.app.domain.repository

import com.carelipik.app.domain.model.DoctorProfile

interface DoctorProfileRepository {
    suspend fun load(): DoctorProfile?
    suspend fun save(profile: DoctorProfile)
}
