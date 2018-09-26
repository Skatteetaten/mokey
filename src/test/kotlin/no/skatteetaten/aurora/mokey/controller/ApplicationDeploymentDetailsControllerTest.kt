package no.skatteetaten.aurora.mokey.controller

// import org.springframework.test.web.servlet.match.MockRestRequestMatchers.jsonPath

import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    ApplicationDeploymentDetailsController::class,
    ApplicationDeploymentDetailsResourceAssembler::class,
    LinkBuilderFactory::class
)
@TestPropertySource(properties = ["boober-api-url=http://localhost"])
@AutoConfigureWebClient
class ApplicationDeploymentDetailsControllerTest : AbstractSecurityControllerTest() {

    private val ID = "123"

    @MockBean
    lateinit var applicationDataService: ApplicationDataService

    val podDetailsDataBuilder = PodDetailsDataBuilder()
    val managementDataBuilder = podDetailsDataBuilder.managementDataBuilder

    val applicationData = ApplicationData(
        "abc123",
        "abc1234",
        AuroraStatus(HEALTHY, "", listOf()),
        "deployTag",
        "name",
        "name-1",
        "namespace",
        "affiliation",
        pods = listOf(podDetailsDataBuilder.build()),
        deployDetails = DeployDetails("Complete", 1, 1),
        addresses = emptyList(),
        deploymentCommand = ApplicationDeploymentCommand(
            auroraConfig = AuroraConfigRef("affiliation", "master", "123"),
            applicationDeploymentRef = ApplicationDeploymentRef("namespace", "name")
        )
    )

    @Test
    @WithUserDetails
    fun `should get applicationdetails given user with access`() {

        given(applicationDataService.findApplicationDataByApplicationDeploymentId(ID)).willReturn(applicationData)

        mockMvc.perform(get("/api/applicationdeploymentdetails/{id}", "123"))
            .andExpect(status().isOk)
            .andExpect(
                jsonPath(
                    "$.podResources[0].managementResponses.health.textResponse",
                    `is`(managementDataBuilder.healthResponseJson)
                )
            )
            .andExpect(
                jsonPath(
                    "$.podResources[0].managementResponses.info.textResponse",
                    `is`(managementDataBuilder.infoResponseJson)
                )
            )
    }
}