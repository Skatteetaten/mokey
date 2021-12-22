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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.ApplicationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.documentationConfiguration
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.reactive.server.WebTestClient

@ExtendWith(RestDocumentationExtension::class)
@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@ExperimentalStdlibApi
@WebFluxTest(WebSecurityConfig::class, ApplicationController::class)
class ApplicationControllerTest {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationResourceAssembler

    private lateinit var webTestClient: WebTestClient

    private val applicationData = ApplicationDataBuilder().build()

    @BeforeEach
    fun setUp(applicationContext: ApplicationContext, restDocumentation: RestDocumentationContextProvider) {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).configureClient()
            .filter(documentationConfiguration(restDocumentation))
            .build()
    }

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
            .get()
            .uri {
                it
                    .path("/api/application")
                    .queryParam("affiliation", "paas")
                    .build()
            }
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
    }
}
