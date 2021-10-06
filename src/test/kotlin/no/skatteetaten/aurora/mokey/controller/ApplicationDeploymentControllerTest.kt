package no.skatteetaten.aurora.mokey.controller

import assertk.assertThat
import assertk.assertions.isFalse
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.contentTypeJson
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.post
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.status
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentResourceBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

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

    @Test
    fun `Return application deployments by refs cached`() {
        every { applicationDataService.findPublicApplicationDataByApplicationDeploymentRef(any()) } returns listOf(
            ApplicationDataBuilder().build().publicData
        )
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentResourceBuilder().build())

        mockMvc.post(
            path = Path("/api/applicationdeployment"),
            body = listOf(ApplicationDeploymentRef("environment", "application")),
        headers = HttpHeaders().contentTypeJson()
        ) {
            statusIsOk().responseJsonPath("$[0].identifier").equalsValue("123")
        }
    }

    @Test
    fun `Return application deployments by refs uncached`() {
        every {
            applicationDataService.findPublicApplicationDataByApplicationDeploymentRef(any(), any())
        } answers {
            assertThat(secondArg<Boolean>()).isFalse()

            listOf(
                ApplicationDataBuilder().build().publicData
            )
        }
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentResourceBuilder().build())

        mockMvc.post(
            path = Path("/api/applicationdeployment?cached=false"),
            body = listOf(ApplicationDeploymentRef("environment", "application")),
            headers = HttpHeaders().contentTypeJson()
        ) {
            statusIsOk().responseJsonPath("$[0].identifier").equalsValue("123")
        }
    }

    @Test
    fun `Return 404 when applicationData is not found`() {
        every { applicationDataService.findPublicApplicationDataByApplicationDeploymentId(any()) } returns null

        mockMvc.get(Path("/api/applicationdeployment/id-not-found")) {
            status(HttpStatus.NOT_FOUND)
        }
    }
}
