package com.carelipik.app.domain.repository

import com.carelipik.app.domain.model.ApprovedConsultation

/** Persists only doctor-approved consultation documentation on this device. */
interface ConsultationRepository {
    suspend fun list(): List<ApprovedConsultation>
    suspend fun get(id: String): ApprovedConsultation?
    suspend fun save(consultation: ApprovedConsultation)
    suspend fun delete(id: String)
}
