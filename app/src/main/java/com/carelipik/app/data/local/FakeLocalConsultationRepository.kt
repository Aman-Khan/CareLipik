package com.carelipik.app.data.local

import com.carelipik.app.domain.repository.ConsultationRepository

/** Prototype-only data source. It never reads or sends patient information. */
class FakeLocalConsultationRepository : ConsultationRepository {
    override fun patientDisplayName(): String = "Sample patient"
}
