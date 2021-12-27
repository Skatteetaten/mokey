package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.TestStubSetup
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.get
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@WebFluxTest(WebSecurityConfig::class, AffiliationController::class)
class AffiliationControllerTest : TestStubSetup() {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @Test
    fun `Return list of affiliations`() {
        every { applicationDataService.findAllAffiliations() } returns listOf("paas", "affiliation1", "affiliation2")

        webTestClient
            .get("/api/affiliation") {
                exchange()
                    .expectStatus().isOk
                    .expectBody()
                    .jsonPath("$.length()").isEqualTo(3)
            }
    }

    @WithMockUser("test", roles = ["test"])
    @Test
    fun `Return list of visible affiliations`() {
        coEvery { applicationDataService.findAllVisibleAffiliations() } returns listOf(
            "paas",
            "affiliation1",
            "affiliation2"
        )

        webTestClient.get("/api/auth/affiliation") {
            exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3)
        }
    }
}
