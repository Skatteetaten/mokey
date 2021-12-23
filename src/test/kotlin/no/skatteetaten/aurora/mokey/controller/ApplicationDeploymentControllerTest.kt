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
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.contentTypeJson
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.get
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.post
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.web.reactive.function.BodyInserters.fromValue

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@ExperimentalStdlibApi
@WebFluxTest(WebSecurityConfig::class, ApplicationDeploymentController::class)
class ApplicationDeploymentControllerTest : TestStubSetup() {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationDeploymentResourceAssembler

    @Test
    fun `Return application deployment by id`() {
        every { applicationDataService.findPublicApplicationDataByApplicationDeploymentId(any()) } returns ApplicationDataBuilder().build().publicData
        every { assembler.toResource(any()) } returns ApplicationDeploymentResourceBuilder().build()

        webTestClient
            .get("/api/applicationdeployment/123") {
                exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.identifier").isEqualTo("123")
            }
    }

    @Test
    fun `Return application deployments by refs cached`() {
        coEvery { applicationDataService.findPublicApplicationDataByApplicationDeploymentRef(any()) } returns listOf(
            ApplicationDataBuilder().build().publicData
        )
        every { assembler.toResources(any()) } returns listOf(ApplicationDeploymentResourceBuilder().build())

        webTestClient
            .post("/api/applicationdeployment") {
                contentTypeJson()
                    .body(fromValue(listOf(ApplicationDeploymentRef("environment", "application"))))
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$[0].identifier").isEqualTo("123")
            }
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
            .post("/api/applicationdeployment?cached=false") {
                contentTypeJson()
                    .body(fromValue(listOf(ApplicationDeploymentRef("environment", "application"))))
                    .exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$[0].identifier").isEqualTo("123")
            }
    }

    @Test
    fun `Return 404 when applicationData is not found`() {
        every { applicationDataService.findPublicApplicationDataByApplicationDeploymentId(any()) } returns null

        webTestClient
            .get("/api/applicationdeployment/id-not-found") {
                exchange()
                    .expectStatus()
                    .isNotFound
            }
    }
}
