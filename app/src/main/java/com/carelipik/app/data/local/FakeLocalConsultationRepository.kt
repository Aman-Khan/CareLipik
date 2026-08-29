package com.carelipik.app.data.local

import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.repository.ConsultationRepository

class FakeLocalConsultationRepository(
    initial: List<ApprovedConsultation> = emptyList()
) : ConsultationRepository {
    private val consultations = initial.associateByTo(linkedMapOf(), ApprovedConsultation::id)

    override suspend fun list(): List<ApprovedConsultation> =
        consultations.values.sortedByDescending(ApprovedConsultation::approvedAtMillis)

    override suspend fun get(id: String): ApprovedConsultation? = consultations[id]

    override suspend fun save(consultation: ApprovedConsultation) {
        consultations[consultation.id] = consultation
    }

    override suspend fun delete(id: String) {
        consultations.remove(id)
    }
}
