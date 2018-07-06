package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData

interface ApplicationDataService {
    fun findAllAffiliations(): List<String>

    fun findApplicationDataByApplicationId(id: String) =
        findAllApplicationData().find { it.applicationId == id }

    fun findApplicationDataByInstanceId(id: String): ApplicationData?

    fun findAllApplicationData(affiliations: List<String>? = null): List<ApplicationData>
}