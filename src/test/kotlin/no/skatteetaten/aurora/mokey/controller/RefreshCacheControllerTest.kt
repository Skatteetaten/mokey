package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.coEvery
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters.fromValue

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@WebFluxTest(WebSecurityConfig::class, RefreshCacheController::class)
class RefreshCacheControllerTest {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        clearMocks(applicationDataService)
    }

    @Test
    fun `Refresh cache with applicationDeploymentId`() {
        webTestClient
            .post()
            .uri("/api/auth/refresh")
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .body(fromValue(RefreshParams(applicationDeploymentId = "123", affiliations = null)))
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `Refresh cache with unknown applicationDeploymentId`() {
        coEvery { applicationDataService.refreshItem(any()) } throws IllegalArgumentException("test exception")

        webTestClient
            .post()
            .uri("/api/auth/refresh")
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .body(fromValue(RefreshParams(applicationDeploymentId = "123", affiliations = null)))
            .exchange()
            .expectStatus()
            .isBadRequest
    }

    @Test
    fun `Refresh cache with affiliations`() {
        webTestClient
            .post()
            .uri("/api/auth/refresh")
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .body(fromValue(RefreshParams(applicationDeploymentId = null, affiliations = listOf("aurora"))))
            .exchange()
            .expectStatus()
            .isOk
    }

    @Test
    fun `Refresh cache missing input`() {
        webTestClient
            .post()
            .uri("/api/auth/refresh")
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .body(fromValue(RefreshParams(applicationDeploymentId = null, affiliations = null)))
            .exchange()
            .expectStatus()
            .isBadRequest
    }
}
