package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationData
import org.junit.jupiter.api.Test

class ApplicationDataServiceTest {

    @Test
    fun `Get ApplicationData by applicationId`() {
        val service = object : ApplicationDataService {
            override fun findAllAffiliations(): List<String> = emptyList()
            override fun findApplicationDataByInstanceId(id: String): ApplicationData? = null

            override fun findAllApplicationData(affiliations: List<String>?) =
                listOf(
                    ApplicationDataBuilder(name = "app1", applicationId = "123").build(),
                    ApplicationDataBuilder(name = "app2", applicationId = "234").build()
                )
        }

        val applicationData = service.findApplicationDataByApplicationId("234")
        assert(applicationData?.name).isNotNull { it.isEqualTo("app2") }
    }
}