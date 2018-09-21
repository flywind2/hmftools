package com.hartwig.hmftools.idgenerator

import com.hartwig.hmftools.idgenerator.ids.PatientId
import com.hartwig.hmftools.idgenerator.ids.SampleId
import org.apache.logging.log4j.LogManager

private val logger = LogManager.getLogger("SamplesInput")

data class SamplesInput(val samples: List<SampleId>, val patientsMap: Map<PatientId, PatientId> = emptyMap()) {
    init {
        validatePatientsMap()
    }

    val canonicalPatients: Set<PatientId> = (samples.map { canonicalId(it.patientId) } + patientsMap.values).toSet()
    val nonCanonicalPatients: Set<PatientId> = patientsMap.keys

    /**
     * returns all samples for this patient, accounting for potential renames
     */
    fun sampleIds(patient: PatientId): Set<SampleId> {
        return patientIds(patient).flatMap { patientId -> samples.filter { it.patientId == patientId } }.toSet()
    }

    /**
     * returns all ids for this patient, accounting for potential renames
     */
    fun patientIds(patient: PatientId): Set<PatientId> {
        val alternateIds = patientsMap.filterValues { it == canonicalId(patient) }.flatMap { it.toPair().toList() }
        return (alternateIds + patient).toSet()
    }

    fun canonicalId(patient: PatientId) = patientsMap[patient] ?: patient

    private fun validatePatientsMap() {
        val chainedCanonicalMappings = patientsMap.count { (patientId, canonicalId) ->
            val canonicalIsRenamed = patientsMap.containsKey(canonicalId)
            if (canonicalIsRenamed) {
                logger.error("Canonical id ${canonicalId.id} for ${patientId.id} is also renamed to ${patientsMap[canonicalId]!!.id}")
            }
            canonicalIsRenamed
        }
        if (chainedCanonicalMappings > 0) {
            error("Renames of canonical patientIds are not allowed")
        }
    }
}