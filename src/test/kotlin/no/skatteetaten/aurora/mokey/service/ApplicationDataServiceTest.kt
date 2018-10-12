package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import org.junit.jupiter.api.Test

class ApplicationDataServiceTest {

    @Test
    fun `Get ApplicationData by applicationId`() {
        val service = object : ApplicationDataService {
            override fun findAllVisibleAffiliations(): List<String> = emptyList()

            override fun findPublicApplicationDataByApplicationDeploymentId(id: String): ApplicationPublicData? = null
            override fun findAllPublicApplicationData(affiliations: List<String>, ids: List<String>): List<ApplicationPublicData> =
                findAllApplicationData(affiliations).map { it.publicData }

            override fun findAllAffiliations(): List<String> = emptyList()
            override fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData? = null

            override fun findAllApplicationData(affiliations: List<String>, ids: List<String>) =
                listOf(
                    ApplicationDataBuilder(name = "app1", applicationId = "123").build(),
                    ApplicationDataBuilder(name = "app2", applicationId = "234").build()
                )
        }

        val applicationData = service.findAllPublicApplicationDataByApplicationId("234")
        assert(applicationData.first().applicationDeploymentName).isNotNull { it.isEqualTo("app2") }
    }
}