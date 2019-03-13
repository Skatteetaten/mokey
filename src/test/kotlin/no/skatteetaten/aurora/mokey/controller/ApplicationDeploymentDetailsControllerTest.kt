package no.skatteetaten.aurora.mokey.controller

// TODO: Fix
/*
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.hamcrest.Matchers.`is`
import org.junit.Ignore
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(
    ApplicationDeploymentDetailsController::class,
    ApplicationDeploymentDetailsResourceAssembler::class,
    LinkBuilderFactory::class
)
@AutoConfigureWebClient
@Ignore("Will not start")
class ApplicationDeploymentDetailsControllerTest : AbstractSecurityControllerTest() {

    private val ID = "123"

    @MockBean
    lateinit var applicationDataService: ApplicationDataService

    val podDetailsDataBuilder = PodDetailsDataBuilder()
    val managementDataBuilder = podDetailsDataBuilder.managementDataBuilder

    val applicationData = ApplicationData(
        pods = listOf(podDetailsDataBuilder.build()),
        deployDetails = DeployDetails(
            availableReplicas = 1,
            targetReplicas = 1,
            phase = "Complete",
            deployTag = "1"
        ),
        addresses = emptyList(),
        deploymentCommand = ApplicationDeploymentCommand(
            auroraConfig = AuroraConfigRef("affiliation", "master", "123"),
            applicationDeploymentRef = ApplicationDeploymentRef("namespace", "name")
        ),
        publicData = ApplicationPublicData(
            applicationId = "abc123",
            applicationDeploymentId = "abc1234",
            applicationName = "name",
            applicationDeploymentName = "name-1",
            auroraStatus = AuroraStatus(HEALTHY),
            affiliation = "affiliation",
            namespace = "namespace",
            deployTag = "deployTag",
            auroraVersion = null,
            dockerImageRepo = null,
            releaseTo = "releaseTo",
            time = Instant.EPOCH
        )
    )

    @Test
    @WithUserDetails
    fun `should get applicationdetails given user with access`() {

        given(applicationDataService.findApplicationDataByApplicationDeploymentId(ID)).willReturn(applicationData)

        mockMvc.perform(get("/api/auth/applicationdeploymentdetails/{id}", "123"))
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
    */