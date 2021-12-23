package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.coEvery
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.contentTypeJson
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.post
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.web.reactive.function.BodyInserters.fromValue

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@WebFluxTest(WebSecurityConfig::class, RefreshCacheController::class)
class RefreshCacheControllerTest : TestStubSetup() {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @BeforeEach
    fun setUp() {
        clearMocks(applicationDataService)
    }

    @Test
    fun `Refresh cache with applicationDeploymentId`() {
        webTestClient.post("/api/auth/refresh") {
            contentTypeJson()
                .body(fromValue(RefreshParams(applicationDeploymentId = "123", affiliations = null)))
                .exchange()
                .expectStatus()
                .isOk
        }
    }

    @Test
    fun `Refresh cache with unknown applicationDeploymentId`() {
        coEvery { applicationDataService.refreshItem(any()) } throws IllegalArgumentException("test exception")

        webTestClient.post("/api/auth/refresh") {
            contentTypeJson()
                .body(fromValue(RefreshParams(applicationDeploymentId = "123", affiliations = null)))
                .exchange()
                .expectStatus()
                .isBadRequest
        }
    }

    @Test
    fun `Refresh cache with affiliations`() {
        webTestClient
            .post("/api/auth/refresh") {
                contentTypeJson()
                    .body(fromValue(RefreshParams(applicationDeploymentId = null, affiliations = listOf("aurora"))))
                    .exchange()
                    .expectStatus()
                    .isOk
            }
    }

    @Test
    fun `Refresh cache missing input`() {
        webTestClient
            .post("/api/auth/refresh") {
                contentTypeJson()
                    .body(fromValue(RefreshParams(applicationDeploymentId = null, affiliations = null)))
                    .exchange()
                    .expectStatus()
                    .isBadRequest
            }
    }
}
