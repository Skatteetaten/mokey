package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentResourceBuilder
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test

class ApplicationDeploymentControllerTest : AbstractSecurityControllerTest() {

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationDeploymentResourceAssembler

    @Test
    fun `Return application deployment by id`() {
        every { applicationDataService.findPublicApplicationDataByApplicationDeploymentId(any()) } returns ApplicationDataBuilder().build().publicData
        every { assembler.toResource(any()) } returns ApplicationDeploymentResourceBuilder().build()

        mockMvc.get(Path("/api/applicationdeployment/{id}", "123")) {
            statusIsOk().responseJsonPath("$.identifier").equalsValue("123")
        }
    }
}
