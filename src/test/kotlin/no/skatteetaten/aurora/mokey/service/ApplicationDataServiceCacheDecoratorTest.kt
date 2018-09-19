package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.DeployDetails
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock

class ApplicationDataServiceCacheDecoratorTest {

    val app1Id = "some_id"
    val app1v1 = ApplicationData(
        app1Id,
        app1Id,
        AuroraStatus(HEALTHY, "", listOf()),
        "default",
        "testapp",
        "aurora",
        "aurora-1",
        "aurora",
        deployDetails = DeployDetails("Complete", 1, 1),
        addresses = emptyList(),
        deploymentCommand = ApplicationDeploymentCommand(
            applicationDeploymentRef = ApplicationDeploymentRef("namespace", "name"),
            auroraConfig = AuroraConfigRef("affiliation", "master")
        )
    )
    val app1v2 = app1v1.copy(deployTag = "prod")

    val sourceApplicationDataService = mock(ApplicationDataServiceOpenShift::class.java)
    val applicationDataService = ApplicationDataServiceCacheDecorator(sourceApplicationDataService)

    @Test
    fun `should update cache from OpenShiftApplicationDataService`() {
        val affiliations = listOf("aurora")

        given(sourceApplicationDataService.findAllApplicationData(affiliations))
            .willReturn(listOf(app1v1))
            .willReturn(listOf(app1v2))

        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isNull()

        applicationDataService.refreshCache(affiliations)
        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isEqualTo(app1v1)

        applicationDataService.refreshCache(affiliations)
        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isEqualTo(app1v2)
    }
}