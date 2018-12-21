package no.skatteetaten.aurora.mokey.contracts

import com.nhaarman.mockito_kotlin.any
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsResourceAssembler
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.context.support.WithUserDetails

@WithUserDetails
open class ApplicationdeploymentdetailsBase : ContractBase() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @MockBean
    private lateinit var assembler: ApplicationDeploymentDetailsResourceAssembler

    @BeforeEach
    fun setUp() {
        withContractResponses(this) {
            val applicationData = ApplicationDataBuilder().build()
            given(applicationDataService.findApplicationDataByApplicationDeploymentId(anyString())).willReturn(
                applicationData
            )
            given(applicationDataService.findAllApplicationData(any(), any())).willReturn(listOf(applicationData))

            given(assembler.toResource(any())).willReturn(it.response("applicationdeploymentdetails"))
            given(assembler.toResources(any())).willReturn(it.response("applicationdeploymentdetailsarray"))
        }
    }
}