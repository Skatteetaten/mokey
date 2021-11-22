package no.skatteetaten.aurora.mokey.controller

import assertk.assertThat
import assertk.assertions.isFalse
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.every
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentResourceBuilder
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
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
@ExperimentalStdlibApi
@WebFluxTest(WebSecurityConfig::class, ApplicationDeploymentController::class)
class ApplicationDeploymentControllerTest {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationDeploymentResourceAssembler

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `Return application deployment by id`() {
        every { applicationDataService.findPublicApplicationDataByApplicationDeploymentId(any()) } returns ApplicationDataBuilder().build().publicData
        every { assembler.toResource(any()) } returns ApplicationDeploymentResourceBuilder().build()

        webTestClient
            .get()
            .uri {
                it
                    .path("/api/applicationdeployment/{id}")
                    .build("123")
            }
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.identifier").isEqualTo("123")
    }

    @Test
    fun `Return application deployments by refs cached`() {
        coEvery { applicationDataService.findPublicApplicationDataByApplicationDeploymentRef(any()) } returns listOf(
            ApplicationDataBuilder().build().publicData
        )
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentResourceBuilder().build())

        webTestClient
            .post()
            .uri("/api/applicationdeployment")
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .body(fromValue(listOf(ApplicationDeploymentRef("environment", "application"))))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[0].identifier").isEqualTo("123")
    }

    @Test
    fun `Return application deployments by refs uncached`() {
        coEvery {
            applicationDataService.findPublicApplicationDataByApplicationDeploymentRef(any(), any())
        } answers {
            assertThat(secondArg<Boolean>()).isFalse()

            listOf(
                ApplicationDataBuilder().build().publicData
            )
        }
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentResourceBuilder().build())

        webTestClient
            .post()
            .uri {
                it
                    .path("/api/applicationdeployment")
                    .queryParam("cached", false)
                    .build()
            }
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .body(fromValue(listOf(ApplicationDeploymentRef("environment", "application"))))
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[0].identifier").isEqualTo("123")
    }

    @Test
    fun `Return 404 when applicationData is not found`() {
        every { applicationDataService.findPublicApplicationDataByApplicationDeploymentId(any()) } returns null

        webTestClient
            .get()
            .uri("/api/applicationdeployment/id-not-found")
            .exchange()
            .expectStatus()
            .isNotFound
    }
}
