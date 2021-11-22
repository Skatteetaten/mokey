package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentDetailsResourceBuilder
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.reactive.server.WebTestClient

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@ExperimentalStdlibApi
@WebFluxTest(WebSecurityConfig::class, ApplicationDeploymentDetailsController::class)
class ApplicationDeploymentDetailsControllerTest {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationDeploymentDetailsResourceAssembler

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `Return application deployment details by id`() {
        coEvery { applicationDataService.findApplicationDataByApplicationDeploymentId(any()) } returns ApplicationDataBuilder().build()
        every { assembler.toResource(any()) } returns ApplicationDeploymentDetailsResourceBuilder().build()

        webTestClient
            .get()
            .uri {
                it
                    .path("/api/auth/applicationdeploymentdetails/{id}")
                    .build("123")
            }
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.identifier").isEqualTo("123")
    }

    @Test
    fun `Return application deployment details by affiliation`() {
        val applicationDatas = listOf(ApplicationDataBuilder().build())

        coEvery { applicationDataService.findAllApplicationData(any(), any()) } returns applicationDatas
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentDetailsResourceBuilder().build())

        webTestClient
            .get()
            .uri {
                it
                    .path("/api/auth/applicationdeploymentdetails")
                    .queryParam("affiliation", "paas")
                    .build()
            }
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[0].identifier").isEqualTo("123")
    }
}
