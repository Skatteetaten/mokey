package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.contentType
import no.skatteetaten.aurora.mockmvc.extensions.post
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentsWithDbResourceBuilder
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.security.test.context.support.WithUserDetails

@WithUserDetails
class ApplicationDeploymentByResourceTest : AbstractSecurityControllerTest() {

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationDeploymentsWithDbResourceAssembler

    @Test
    fun `Return application deployment by resource`() {
        every { applicationDataService.getFromCacheForUser() } returns listOf(ApplicationDataBuilder().build())
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentsWithDbResourceBuilder().build())

        mockMvc.post(
            path = Path("/api/auth/applicationdeploymentbyresource/databases"),
            headers = HttpHeaders().contentType(),
            body = listOf("123", "456")
        ) {
            statusIsOk().responseJsonPath("$[0].identifier").equalsValue("123")
        }
    }
}
