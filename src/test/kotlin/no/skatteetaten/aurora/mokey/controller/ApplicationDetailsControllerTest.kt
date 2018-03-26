package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.databind.node.MissingNode
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.model.*
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.mokey.service.HealthResponse
import no.skatteetaten.aurora.mokey.service.HealthStatus
import no.skatteetaten.aurora.mokey.service.ManagementLinks
import no.skatteetaten.aurora.utils.Right
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
        val applicationData = ApplicationData(
                ApplicationId("name", Environment("env", "affiliation")).toString(),
                AuroraStatus(AuroraStatusLevel.HEALTHY, ""),
                "deployTag",
                "name",
                "namespace",
                "affiliation",
                deployDetails = DeployDetails("Complete", 1, 1),
                pods = listOf(
                        PodDetails(
                                OpenShiftPodExcerpt("pod-1", "OK", 0, true, "10.0.0.1", "", null),
                                Right(ManagementData(
                                        ManagementLinks(emptyMap()),
                                        Right(MissingNode.getInstance()),
                                        Right(HealthResponse(HealthStatus.UP)),
                                        Right(MissingNode.getInstance())
                                ))
                        ))
        )

        given(applicationDataService.findApplicationDataById(ID)).willReturn(applicationData)

        mockMvc.perform(get("/api/applicationdetails/{id}", "123"))
                .andExpect(status().isOk)
    }
}