package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationResourceBuilder
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.get
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@ExperimentalStdlibApi
@WebFluxTest(WebSecurityConfig::class, ApplicationController::class)
class ApplicationControllerTest : TestStubSetup() {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationResourceAssembler

    private val applicationData = ApplicationDataBuilder().build()

    @Test
    fun `Return application by id`() {
        every { applicationDataService.findAllPublicApplicationDataByApplicationId(any()) } returns listOf(applicationData.publicData)
        every { assembler.toResource(any()) } returns ApplicationResourceBuilder().build()

        webTestClient
            .get("/api/application/abc123") {
                exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.identifier").isEqualTo("123")
            }
    }

    @Test
    fun `Return applications for affiliation`() {
        every { applicationDataService.findAllPublicApplicationData(any(), any()) } returns listOf(applicationData.publicData)
        every { assembler.toResources(any()) } returns listOf(ApplicationResourceBuilder().build())

        webTestClient
            .get(path = "/api/application", uriBuilder = {
                queryParam("affiliation", "paas")
            }) {
                exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(1)
            }
    }
}
