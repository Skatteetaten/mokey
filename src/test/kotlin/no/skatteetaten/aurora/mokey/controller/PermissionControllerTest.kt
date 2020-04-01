package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.fabric8.kubernetes.api.model.authorization.SelfSubjectAccessReviewBuilder
import io.mockk.coEvery
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.NamespaceDataBuilder
import no.skatteetaten.aurora.mokey.service.OpenShiftUserClient
import org.junit.jupiter.api.Test
import org.springframework.security.test.context.support.WithUserDetails

@WithUserDetails
class PermissionControllerTest : AbstractSecurityControllerTest() {

    @MockkBean
    private lateinit var openShiftUserClient: OpenShiftUserClient

    @Test
    fun `Check permissions`() {
        coEvery { openShiftUserClient.getNamespaceByNameOrNull("aurora") } returns NamespaceDataBuilder().build()
        coEvery { openShiftUserClient.selfSubjectAccessReview(any()) } returns SelfSubjectAccessReviewBuilder().build()

        mockMvc.get(Path("/api/auth/permissions/aurora")) {
            statusIsOk()
                .responseJsonPath("$.view").isTrue()
                .responseJsonPath("$.admin").isFalse()
                .responseJsonPath("$.namespace").equalsValue("aurora")
        }
    }
}
