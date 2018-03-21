package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
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
    lateinit var applicationDataService: ApplicationDataService

    @Test
    @WithUserDetails
    fun `should get applicationdetails given user with access`() {
        given(applicationDataService.findApplicationDataById(ID)).willReturn(
                ApplicationData(
                        ApplicationId("name", Environment("env", "affiliation")).toString(),
                        AuroraStatus(AuroraStatusLevel.HEALTHY, ""),
                        "deployTag",
                        "name",
                        "namespace",
                        "affiliation",
                        deployDetails = DeployDetails("Complete", 1, 1)
                )
        )

        mockMvc.perform(get("/api/applicationdetails/{id}", "123"))
                .andExpect(status().isOk)
    }
}