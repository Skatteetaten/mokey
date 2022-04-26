package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import no.skatteetaten.aurora.mokey.StorageGridObjectAreaBuilder
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.StorageGridObjectAreaService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.TestStubSetup
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.get
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@WebFluxTest(WebSecurityConfig::class, StorageGridObjectAreaController::class)
class StorageGridObjectAreaControllerTest : TestStubSetup() {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean
    private lateinit var storageGridObjectAreaService: StorageGridObjectAreaService

    @Test
    fun `Return StorageGridObjectAreas`() {
        coEvery {
            storageGridObjectAreaService.findAllStorageGridObjectAreasForAffiliation(any())
        } returns listOf(StorageGridObjectAreaBuilder().build())

        webTestClient
            .get("/api/auth/storagegridobjectarea?affiliation=aup") {
                exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.[0].objectArea").isEqualTo("referanse-java2")
            }
    }
}
