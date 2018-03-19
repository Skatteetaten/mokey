package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.CachedApplicationDataService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ApplicationDetailsController::class)
@AutoConfigureWebClient
class ApplicationDetailsControllerTest : AbstractSecurityControllerTest() {

    private val ID = "123"

    @MockBean
    lateinit var cachedApplicationDataService: CachedApplicationDataService
/*

    @Test
    @WithUserDetails
    fun `Get AuroraDetails given user with access`() {
        given(cachedApplicationDataService.findApplicationDataById(ID)).willReturn(
                ApplicationData(
                        ApplicationId("name", Environment("env", "affiliation")),
                        "deployTag",
                        "name",
                        "namespace",
                        "affiliation",
                        availableReplicas = 1,
                        targetReplicas = 1
                )
        )

        mockMvc.perform(get("/api/applicationdetails/{id}", "123"))
                .andExpect(status().isOk)
    }
*/
}