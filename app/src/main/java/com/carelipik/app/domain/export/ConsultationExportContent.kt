package com.carelipik.app.domain.export

import com.carelipik.app.domain.model.ApprovedConsultation
import java.time.Instant
import java.util.UUID

object ConsultationExportContent {
    fun structuredJson(consultation: ApprovedConsultation): String {
        val sections = consultation.draft.effectiveSections.joinToString(",\n") { section ->
            val sourceTurnIds = section.sourceTurnIds.joinToString(", ") { it.json() }
            """
              {
                "id": ${section.id.json()},
                "title": ${section.title.json()},
                "content": ${section.content.json()},
                "sourceTurnIds": [$sourceTurnIds]
              }
            """.trimIndent().prependIndent("          ")
        }
        val medications = consultation.draft.medications.joinToString(",\n") { medication ->
            """
              {
                "name": ${medication.name.json()},
                "genericName": ${medication.genericName.json()},
                "strength": ${medication.strength.json()},
                "dose": ${medication.dose.json()},
                "route": ${medication.route.json()},
                "frequency": ${medication.frequency.json()},
                "duration": ${medication.duration.json()},
                "instructions": ${medication.instructions.json()},
                "sourceEvidence": ${medication.sourceEvidence.json()},
                "doctorReviewed": ${medication.isDoctorReviewed}
              }
            """.trimIndent().prependIndent("          ")
        }
        val warnings = consultation.draft.coverageWarnings.joinToString(", ") { it.json() }
        return """
        {
          "schema": "https://carelipik.app/schemas/approved-consultation/v2",
          "schemaVersion": 2,
          "exportType": "doctor-approved-clinical-note",
          "consultationId": ${consultation.id.json()},
          "approvedAt": ${approvedAt(consultation).json()},
          "patient": {
            "reference": ${consultation.patientName.json()},
            "age": ${consultation.patientAge.json()},
            "visitReason": ${consultation.visitReason.json()}
          },
          "clinicalNote": {
            "format": ${consultation.draft.noteFormat.name.json()},
            "formatDisplayName": ${consultation.draft.noteFormat.displayName.json()},
            "language": ${consultation.draft.noteLanguage.name.json()},
            "specialty": ${consultation.draft.specialtyName.json()},
            "generationSource": ${consultation.draft.generationSource.name.json()},
            "sections": [
        $sections
            ],
            "prescribedMedications": [
        $medications
            ],
            "coverageWarningsAtApproval": [$warnings],
            "reviewedTranscript": ${consultation.draft.reviewedTranscript.json()}
          },
          "doctorApproved": true,
          "includesConsultationAudio": false
        }
        """.trimIndent() + "\n"
    }

    fun plainText(consultation: ApprovedConsultation): String {
        val sections = consultation.draft.effectiveSections.joinToString("\n\n") { section ->
            "${section.title.uppercase()}\n${section.content.ifBlank { "Not documented" }}"
        }
        val medications = consultation.draft.medications.ifEmpty { emptyList() }
            .joinToString("\n") { medication -> "• ${medication.displayText()}" }
            .ifBlank { "No prescribed medicines documented" }
        val warnings = consultation.draft.coverageWarnings.joinToString("\n") { "• $it" }
            .ifBlank { "None" }
        return """
        CARELIPIK - DOCTOR-APPROVED CLINICAL NOTE

        Consultation ID: ${consultation.id}
        Approved at: ${approvedAt(consultation)}
        Patient / reference: ${consultation.patientName}
        Age: ${consultation.patientAge.ifBlank { "Not recorded" }}
        Visit reason: ${consultation.visitReason.ifBlank { "Not recorded" }}
        Note format: ${consultation.draft.noteFormat.displayName}
        Note language: ${consultation.draft.noteLanguage.displayName}
        Draft source: ${consultation.draft.generationSource.displayName}

        $sections

        PRESCRIBED MEDICINES AND DOSAGES
        $medications

        TRANSCRIPT COVERAGE WARNINGS AT APPROVAL
        $warnings

        COMPLETE REVIEWED CONVERSATION LOG
        ${consultation.draft.reviewedTranscript.ifBlank { "Not available for this older record" }}

        CareLipik assists with documentation. Clinical accuracy remains the doctor's responsibility.
        Consultation audio is not included.
        """.trimIndent() + "\n"
    }

    fun fhirR4Bundle(consultation: ApprovedConsultation): String {
        val bundleId = "bundle-${consultation.id}"
        val compositionId = "composition-${consultation.id}"
        val patientId = "patient-${consultation.id}"
        val deviceId = "carelipik-author"
        val documentReferenceId = "document-reference-${consultation.id}"
        val compositionFullUrl = resourceFullUrl("Composition", compositionId)
        val patientFullUrl = resourceFullUrl("Patient", patientId)
        val deviceFullUrl = resourceFullUrl("Device", deviceId)
        val documentReferenceFullUrl = resourceFullUrl(
            "DocumentReference",
            documentReferenceId
        )
        val approvedAt = approvedAt(consultation)
        val noteSections = consultation.draft.effectiveSections.map { section ->
            section.title to section.content
        }
        val medicationText = consultation.draft.medications
            .joinToString("\n") { "• ${it.displayText()}" }
            .ifBlank { "No prescribed medicines documented" }
        val coverageText = consultation.draft.coverageWarnings
            .joinToString("\n") { "• $it" }
            .ifBlank { "None" }
        val medicationEntries = consultation.draft.medications.mapIndexed { index, medication ->
            val medicationRequestId = "medication-request-${consultation.id}-$index"
            val medicationRequestFullUrl = resourceFullUrl(
                "MedicationRequest",
                medicationRequestId
            )
            """
                {
                  "fullUrl": ${medicationRequestFullUrl.json()},
                  "resource": {
                    "resourceType": "MedicationRequest",
                    "id": ${medicationRequestId.json()},
                    "status": "active",
                    "intent": "order",
                    "medicationCodeableConcept": {
                      "text": ${listOf(
                          medication.name,
                          medication.genericName,
                          medication.strength
                      ).filter(String::isNotBlank).joinToString(" ").json()}
                    },
                    "subject": { "reference": ${patientFullUrl.json()} },
                    "authoredOn": ${approvedAt.json()},
                    "dosageInstruction": [{
                      "text": ${medication.displayText().json()}
                    }],
                    "note": [{
                      "text": "Doctor reviewed this transcript-derived medication entry before approval."
                    }]
                  }
                }
            """.trimIndent().prependIndent("                ")
        }.joinToString(",\n", prefix = if (consultation.draft.medications.isEmpty()) "" else ",\n")
        val sections = (listOf(
            "Patient details" to "Age: ${consultation.patientAge.ifBlank { "Not recorded" }}. " +
                "Visit reason: ${consultation.visitReason.ifBlank { "Not recorded" }}.",
            "Clinical note format" to consultation.draft.noteFormat.displayName
        ) + noteSections + listOf(
            "Prescribed medicines and dosages" to medicationText,
            "Transcript coverage warnings at approval" to coverageText,
            "Complete reviewed conversation log" to consultation.draft.reviewedTranscript
        )).joinToString(",\n") { (title, text) ->
            """
                {
                  "title": ${title.json()},
                  "text": {
                    "status": "generated",
                    "div": ${"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>${text.ifBlank { "Not documented" }.xml()}</p></div>".json()}
                  }
                }
            """.trimIndent().prependIndent("          ")
        }
        return """
            {
              "resourceType": "Bundle",
              "id": ${bundleId.json()},
              "identifier": {
                "system": "urn:carelipik:consultation",
                "value": ${consultation.id.json()}
              },
              "type": "document",
              "timestamp": ${approvedAt.json()},
              "entry": [
                {
                  "fullUrl": ${compositionFullUrl.json()},
                  "resource": {
                    "resourceType": "Composition",
                    "id": ${compositionId.json()},
                    "status": "final",
                    "type": {
                      "coding": [{
                        "system": "http://loinc.org",
                        "code": "11488-4",
                        "display": "Consult note"
                      }],
                      "text": "Doctor-approved clinical note"
                    },
                    "subject": { "reference": ${patientFullUrl.json()} },
                    "date": ${approvedAt.json()},
                    "author": [{ "reference": ${deviceFullUrl.json()} }],
                    "title": "Doctor-approved clinical note",
                    "confidentiality": "N",
                    "attester": [{
                      "mode": "professional",
                      "time": ${approvedAt.json()}
                    }],
                    "section": [
            $sections
                    ]
                  }
                },
                {
                  "fullUrl": ${patientFullUrl.json()},
                  "resource": {
                    "resourceType": "Patient",
                    "id": ${patientId.json()},
                    "identifier": [{
                      "system": "urn:carelipik:patient-reference",
                      "value": ${consultation.patientName.json()}
                    }],
                    "name": [{ "text": ${consultation.patientName.json()} }]
                  }
                }$medicationEntries,
                {
                  "fullUrl": ${deviceFullUrl.json()},
                  "resource": {
                    "resourceType": "Device",
                    "id": ${deviceId.json()},
                    "status": "active",
                    "deviceName": [{
                      "name": "CareLipik clinical documentation assistant",
                      "type": "manufacturer-name"
                    }]
                  }
                },
                {
                  "fullUrl": ${documentReferenceFullUrl.json()},
                  "resource": {
                    "resourceType": "DocumentReference",
                    "id": ${documentReferenceId.json()},
                    "masterIdentifier": {
                      "system": "urn:carelipik:consultation",
                      "value": ${consultation.id.json()}
                    },
                    "status": "current",
                    "type": {
                      "coding": [{
                        "system": "http://loinc.org",
                        "code": "11488-4",
                        "display": "Consult note"
                      }]
                    },
                    "subject": { "reference": ${patientFullUrl.json()} },
                    "date": ${approvedAt.json()},
                    "author": [{ "reference": ${deviceFullUrl.json()} }],
                    "content": [{
                      "attachment": {
                        "contentType": "application/fhir+json",
                        "url": ${"urn:carelipik:consultation:${consultation.id}".json()},
                        "title": "Doctor-approved clinical note"
                      }
                    }]
                  }
                }
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun approvedAt(consultation: ApprovedConsultation): String =
        Instant.ofEpochMilli(consultation.approvedAtMillis).toString()

    private fun resourceFullUrl(resourceType: String, resourceId: String): String =
        "urn:uuid:${UUID.nameUUIDFromBytes("$resourceType/$resourceId".toByteArray()).toString()}"

    private fun com.carelipik.app.domain.model.MedicationDraft.displayText(): String =
        buildList {
            add(name.ifBlank { "Unnamed medicine" })
            if (genericName.isNotBlank()) add("Generic/salt: $genericName")
            if (strength.isNotBlank()) add("Strength: $strength")
            if (dose.isNotBlank()) add("Dose: $dose")
            if (route.isNotBlank()) add("Route: $route")
            if (frequency.isNotBlank()) add("Frequency: $frequency")
            if (duration.isNotBlank()) add("Duration: $duration")
            if (instructions.isNotBlank()) add("Instructions: $instructions")
        }.joinToString("; ")

    private fun String.json(): String = buildString {
        append('"')
        this@json.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun String.xml(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
