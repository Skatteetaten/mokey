package no.skatteetaten.aurora.mokey.contracts

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.any
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentCommandResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentRefResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentResourceAssembler
import no.skatteetaten.aurora.mokey.controller.AuroraConfigRefResource
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean

open class ApplicationdeploymentBase : ContractBase() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @MockBean
    private lateinit var assembler: ApplicationDeploymentResourceAssembler

    @BeforeEach
    fun setUp() {
        withContractResponses(this) {
            val applicationData = ApplicationDataBuilder().build()
            given(applicationDataService.findPublicApplicationDataByApplicationDeploymentId(ArgumentMatchers.anyString())).willReturn(
                applicationData.publicData
            )

            given(assembler.toResource(any())).willReturn(it.response())
        }
    }
}