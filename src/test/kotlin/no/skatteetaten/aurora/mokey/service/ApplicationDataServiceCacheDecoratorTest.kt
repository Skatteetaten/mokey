package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
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
        applicationId = app1Id,
        applicationDeploymentId = app1Id,
        auroraStatus = AuroraStatus(HEALTHY, "", listOf()),
        deployTag = "default",
        applicationName = "testapp",
        applicationDeploymentName = "aurora",
        namespace = "aurora-1",
        affiliation = "aurora",
        deployDetails = DeployDetails("Complete", 1, 1),
        addresses = emptyList(),
        deploymentCommand = ApplicationDeploymentCommand(
            applicationDeploymentRef = ApplicationDeploymentRef("namespace", "name"),
            auroraConfig = AuroraConfigRef("affiliation", "master")
        )
    )
    val app1v2 = app1v1.copy(deployTag = "prod")

    val sourceApplicationDataService = mock(ApplicationDataServiceOpenShift::class.java)
    val openshiftService = mock(OpenShiftService::class.java)
    val applicationDataService = ApplicationDataServiceCacheDecorator(sourceApplicationDataService, openshiftService)

    @Test
    fun `should update cache from OpenShiftApplicationDataService`() {
        val affiliations = listOf("aurora")

        given(sourceApplicationDataService.findAllApplicationData(affiliations))
            .willReturn(listOf(app1v1))
            .willReturn(listOf(app1v2))

        given(openshiftService.currentUserHasAccess(app1v1.namespace)).willReturn(true)
        // assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isNull()

        applicationDataService.refreshCache(affiliations)
        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isEqualTo(app1v1)

        applicationDataService.refreshCache(affiliations)
        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isEqualTo(app1v2)
    }

    @Test
    fun `should return empty response if current user has no access`() {
        val affiliations = listOf("aurora")

        given(sourceApplicationDataService.findAllApplicationData(affiliations))
            .willReturn(listOf(app1v1))

        applicationDataService.refreshCache(affiliations)
        given(openshiftService.currentUserHasAccess(app1v1.namespace)).willReturn(false)
        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isNull()
    }

    @Test
    fun `should skip application if we do not have access`() {
        val affiliations = listOf("aurora")

        given(sourceApplicationDataService.findAllApplicationData(affiliations))
            .willReturn(listOf(app1v1))
        applicationDataService.refreshCache(affiliations)
        given(openshiftService.userProjectNames()).willReturn(emptySet())
        assertThat(applicationDataService.findAllApplicationData(affiliations)).isEmpty()
    }

}