package no.skatteetaten.aurora.mokey.controller

import com.nhaarman.mockito_kotlin.any
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.mock.withContractResponse
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithUserDetails

@WithUserDetails
class ApplicationDeploymentDetailsControllerTest : AbstractSecurityControllerTest() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @MockBean
    private lateinit var assembler: ApplicationDeploymentDetailsResourceAssembler

    @Test
    fun `Return application deployment details by id`() {
        given(applicationDataService.findApplicationDataByApplicationDeploymentId(ArgumentMatchers.anyString()))
            .willReturn(ApplicationDataBuilder().build())

        val applicationDeploymentDetails = given(assembler.toResource(any()))
            .withContractResponse("applicationdeploymentdetails/applicationdeploymentdetails") { willReturn(content) }
            .mockResponse

        mockMvc.get(Path("/api/auth/applicationdeploymentdetails/{id}", "123")) {
            statusIsOk().responseJsonPath("$").equalsObject(applicationDeploymentDetails)
        }
    }

    @Test
    fun `Return application deployment details by affiliation`() {
        given(applicationDataService.findAllApplicationData(any(), any()))
            .willReturn(listOf(ApplicationDataBuilder().build()))

        val applicationDeploymentDetails = given(assembler.toResources((any())))
            .withContractResponse("applicationdeploymentdetails/applicationdeploymentdetailsarray") { willReturn(content) }
            .mockResponse

        mockMvc.get(Path("/api/auth/applicationdeploymentdetails?affiliation=paas")) {
            statusIsOk().responseJsonPath("$[0]").equalsObject(applicationDeploymentDetails.first())
        }
    }
}