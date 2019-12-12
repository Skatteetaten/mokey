package no.skatteetaten.aurora.mokey.controller

import com.nhaarman.mockito_kotlin.any
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.contentType
import no.skatteetaten.aurora.mockmvc.extensions.mock.withContractResponse
import no.skatteetaten.aurora.mockmvc.extensions.post
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.security.test.context.support.WithUserDetails

@WithUserDetails
class ApplicationDeploymentByResourceTest : AbstractSecurityControllerTest() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @MockBean
    private lateinit var assembler: ApplicationDeploymentsWithDbResourceAssembler

    @Test
    fun `Return application deploylment by resource`() {
        given(applicationDataService.getFromCacheForUser()).willReturn(
            listOf(ApplicationDataBuilder().build())
        )

        val applicationDeployment = given(assembler.toResources(any()))
            .withContractResponse("applicationdeployment/applicationdeploymentwithdb") { willReturn(content) }
            .mockResponse

        mockMvc.post(
            path = Path("/api/auth/applicationdeploymentbyresource/databases"),
            headers = HttpHeaders().contentType(),
            body = listOf("123", "456")
        ) {
            statusIsOk().responseJsonPath("$[0]").equalsObject(applicationDeployment.first())
        }
    }
}