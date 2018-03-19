package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData

interface ApplicationDataService {
    fun getAffiliations(): List<String>

    fun findApplicationDataById(id: String): ApplicationData?

    fun findAllApplicationData(affiliations: List<String>? = null): List<ApplicationData>
}