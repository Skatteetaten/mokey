package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.model.*
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ApplicationDetailsController::class,
        ApplicationDetailsResourceAssembler::class,
        LinkBuilderFactory::class)
@TestPropertySource(properties = ["boober-api-url=http://localhost"])
@AutoConfigureWebClient
class ApplicationDetailsControllerTest : AbstractSecurityControllerTest() {

    private val ID = "123"

    @MockBean
    lateinit var applicationDataService: ApplicationDataService

    @Test
    @WithUserDetails
    fun `should get applicationdetails given user with access`() {
        val applicationData = ApplicationData(
                ApplicationId("name", Environment("env", "affiliation")).toString(),
                AuroraStatus(AuroraStatusLevel.HEALTHY, ""),
                "deployTag",
                "name",
                "namespace",
                "affiliation",
                pods = listOf(PodDetailsDataBuilder().build()),
                deployDetails = DeployDetails("Complete", 1, 1),
                addresses = emptyList()
        )

        given(applicationDataService.findApplicationDataById(ID)).willReturn(applicationData)

        mockMvc.perform(get("/api/applicationdetails/{id}", "123"))
                .andExpect(status().isOk)
    }
}