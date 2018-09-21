package com.hartwig.hmftools.idgenerator

import com.hartwig.hmftools.idgenerator.anonymizedIds.HmfPatientId
import com.hartwig.hmftools.idgenerator.ids.PatientId

class AnonymizedPatients(password: String, private val hmfPatientIds: Collection<HmfPatientId>, private val samplesInput: SamplesInput) :
        Collection<HmfPatientId> by hmfPatientIds {
    private val generator = IdGenerator(password)
    private val hmfPatientIdPerHash = hmfPatientIds.associateBy { it.hash }

    operator fun get(patientId: PatientId): HmfPatientId? = hmfPatientIdPerHash[canonicalHash(patientId)]

    private fun canonicalPatientId(patientId: PatientId) = samplesInput.patientsMap[patientId] ?: patientId
    private fun canonicalHash(patientId: PatientId) = generator.hash(canonicalPatientId(patientId).id)
}