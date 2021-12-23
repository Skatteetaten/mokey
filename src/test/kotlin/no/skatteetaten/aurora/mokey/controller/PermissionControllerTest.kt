package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.fabric8.kubernetes.api.model.authorization.SelfSubjectAccessReviewBuilder
import io.mockk.coEvery
import no.skatteetaten.aurora.mokey.NamespaceDataBuilder
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.OpenShiftUserClient
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.TestStubSetup
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.get
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@WebFluxTest(WebSecurityConfig::class, PermissionController::class)
class PermissionControllerTest : TestStubSetup() {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean
    private lateinit var openShiftUserClient: OpenShiftUserClient

    @Test
    fun `Check permissions`() {
        coEvery { openShiftUserClient.getNamespaceByNameOrNull("aurora") } returns NamespaceDataBuilder().build()
        coEvery { openShiftUserClient.selfSubjectAccessReview(any()) } returns SelfSubjectAccessReviewBuilder().build()

        webTestClient
            .get("/api/auth/permissions/aurora") {
                exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.view").isEqualTo(true)
                    .jsonPath("$.admin").isEqualTo(false)
                    .jsonPath("$.namespace").isEqualTo("aurora")
            }
    }
}
