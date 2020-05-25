package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentDetailsResourceBuilder
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.springframework.security.test.context.support.WithUserDetails

@WithUserDetails
class ApplicationDeploymentDetailsControllerTest : AbstractSecurityControllerTest() {

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationDeploymentDetailsResourceAssembler

    @Test
    fun `Return application deployment details by id`() {
        every { applicationDataService.findApplicationDataByApplicationDeploymentId(any()) } returns ApplicationDataBuilder().build()
        every { assembler.toResource(any()) } returns ApplicationDeploymentDetailsResourceBuilder().build()

        mockMvc.get(Path("/api/auth/applicationdeploymentdetails/{id}", "123")) {
            statusIsOk().responseJsonPath("$.identifier").equalsValue("123")
        }
    }

    @Test
    fun `Return application deployment details by affiliation`() {
        val applicationDatas = listOf(ApplicationDataBuilder().build())

        every { applicationDataService.findAllApplicationData(any(), any()) } returns applicationDatas
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentDetailsResourceBuilder().build())

        mockMvc.get(Path("/api/auth/applicationdeploymentdetails?affiliation=paas")) {
            statusIsOk().responseJsonPath("$[0].identifier").equalsValue("123")
        }
    }
}
