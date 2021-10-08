package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.fabric8.kubernetes.api.model.authorization.SelfSubjectAccessReviewBuilder
import io.mockk.coEvery
import no.skatteetaten.aurora.mokey.NamespaceDataBuilder
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.OpenShiftUserClient
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.reactive.server.WebTestClient

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@WebFluxTest(WebSecurityConfig::class, PermissionController::class)
class PermissionControllerTest {
    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean
    private lateinit var openShiftUserClient: OpenShiftUserClient

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `Check permissions`() {
        coEvery { openShiftUserClient.getNamespaceByNameOrNull("aurora") } returns NamespaceDataBuilder().build()
        coEvery { openShiftUserClient.selfSubjectAccessReview(any()) } returns SelfSubjectAccessReviewBuilder().build()

        webTestClient
            .get()
            .uri("/api/auth/permissions/aurora")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.view").isEqualTo(true)
            .jsonPath("$.admin").isEqualTo(false)
            .jsonPath("$.namespace").isEqualTo("aurora")
    }
}
