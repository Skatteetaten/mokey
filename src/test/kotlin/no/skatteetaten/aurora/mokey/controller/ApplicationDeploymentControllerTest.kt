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

class ApplicationDeploymentControllerTest : AbstractSecurityControllerTest() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @MockBean
    private lateinit var assembler: ApplicationDeploymentResourceAssembler

    @Test
    fun `Return application deployment by id`() {
        given(applicationDataService.findPublicApplicationDataByApplicationDeploymentId(ArgumentMatchers.anyString()))
            .willReturn(ApplicationDataBuilder().build().publicData)

        val applicationDeployment = given(assembler.toResource(any()))
            .withContractResponse(name = "applicationdeployment/applicationdeployment") { willReturn(content) }
            .mockResponse

        mockMvc.get(Path("/api/applicationdeployment/{id}", "123")) {
            statusIsOk().responseJsonPath("$").equalsObject(applicationDeployment)
        }
    }
}