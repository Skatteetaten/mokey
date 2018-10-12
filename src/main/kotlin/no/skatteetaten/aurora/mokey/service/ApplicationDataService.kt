package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData

interface ApplicationDataService {
    fun findAllAffiliations(): List<String>

    fun findAllVisibleAffiliations(): List<String>

    fun findAllPublicApplicationDataByApplicationId(id: String): List<ApplicationPublicData> =
        findAllPublicApplicationData().filter { it.applicationId == id }

    fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData?

    fun findAllApplicationData(affiliations: List<String>, ids: List<String> = emptyList()): List<ApplicationData>

    fun findPublicApplicationDataByApplicationDeploymentId(id: String): ApplicationPublicData?

    fun findAllPublicApplicationData(affiliations: List<String> = emptyList(), ids: List<String> = emptyList()): List<ApplicationPublicData>
}