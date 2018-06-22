package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData

interface ApplicationDataService {
    fun findAllAffiliations(): List<String>

    fun findApplicationDataById(id: String): ApplicationData?

    fun findApplicationDataByName(name: String): List<ApplicationData> =
        findAllApplicationData().filter { it.name == name }

    fun findAllApplicationData(affiliations: List<String>? = null): List<ApplicationData>
}