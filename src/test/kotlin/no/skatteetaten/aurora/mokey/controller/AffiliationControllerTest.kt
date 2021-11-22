package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
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
@WebFluxTest(WebSecurityConfig::class, AffiliationController::class)
class AffiliationControllerTest {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `Return list of affiliations`() {
        every { applicationDataService.findAllAffiliations() } returns listOf("paas", "affiliation1", "affiliation2")

        webTestClient
            .get()
            .uri("/api/affiliation")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
    }

    @WithMockUser("test", roles = ["test"])
    @Test
    fun `Return list of visible affiliations`() {
        coEvery { applicationDataService.findAllVisibleAffiliations() } returns listOf(
            "paas",
            "affiliation1",
            "affiliation2"
        )

        webTestClient
            .get()
            .uri("/api/auth/affiliation")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(3)
    }
}
