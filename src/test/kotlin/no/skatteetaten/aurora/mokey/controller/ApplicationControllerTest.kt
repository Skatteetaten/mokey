package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ApplicationController::class)
@AutoConfigureWebClient
class ApplicationControllerTest : AbstractSecurityControllerTest() {

    private val NAMESPACE = "aurora"
    private val APP = "reference"

    @MockBean
    lateinit var openShiftService: OpenShiftService

    @MockBean
    lateinit var cacheService: AuroraApplicationCacheService

    @Test
    @WithUserDetails
    fun `Get AuroraApplication given user with access`() {
        val id = ApplicationId("reference", Environment("aurora", "aurora")).toString()
        given(cacheService.get(id)).willReturn(loadApplication("app.json"))
        given(openShiftService.currentUserHasAccess(NAMESPACE)).willReturn(true)

        mockMvc.perform(get("/api/namespace/{namespace}/application/{name}", NAMESPACE, APP))
                .andExpect(status().isOk)
    }
}