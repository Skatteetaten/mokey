package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData

interface ApplicationDataService {
    fun findAllAffiliations(): List<String>

    fun findPublicApplicationDataByApplicationId(id: String) =
        findAllPublicApplicationData().find { it.applicationId == id }

    fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData?

    fun findAllApplicationData(affiliations: List<String>? = null): List<ApplicationData>

    fun findPublicApplicationDataByApplicationDeploymentId(id: String): ApplicationPublicData?

    fun findAllPublicApplicationData(affiliations: List<String>? = null): List<ApplicationPublicData>
}