package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentsWithDbResourceBuilder
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters.fromValue

@Suppress("unused")
@ExperimentalStdlibApi
@WebFluxTest(WebSecurityConfig::class, ApplicationDeploymentByResourceController::class)
@Import(ApplicationDataService::class)
class ApplicationDeploymentByResourceTest {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationDeploymentsWithDbResourceAssembler

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @WithMockUser("test", roles = ["test"])
    @Test
    fun `Return application deployment by resource`() {
        coEvery { applicationDataService.getFromCacheForUser() } returns listOf(ApplicationDataBuilder().build())
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentsWithDbResourceBuilder().build())

        webTestClient
            .post()
            .uri("/api/auth/applicationdeploymentbyresource/databases")
            .body(fromValue(listOf("123", 456)))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[0].identifier").isEqualTo("123")
    }
}
