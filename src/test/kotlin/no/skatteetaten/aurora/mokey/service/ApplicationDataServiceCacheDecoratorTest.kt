package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.openshift.api.model.Project
import io.micrometer.core.instrument.Meter.Id
import io.micrometer.core.instrument.Meter.Type
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.Environment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import java.time.Instant

class ApplicationDataServiceCacheDecoratorTest {

    val app1Id = "some_id"
    val app1v1 = ApplicationData(
        deployDetails = DeployDetails(
            availableReplicas = 1,
            targetReplicas = 1,
            phase = "Complete",
            deployTag = "1"
        ),
        addresses = emptyList(),
        deploymentCommand = ApplicationDeploymentCommand(
            applicationDeploymentRef = ApplicationDeploymentRef("namespace", "name"),
            auroraConfig = AuroraConfigRef("affiliation", "master")
        ),
        publicData = ApplicationPublicData(
            applicationId = app1Id,
            applicationDeploymentId = app1Id,
            auroraStatus = AuroraStatus(HEALTHY),
            deployTag = "default",
            applicationName = "testapp",
            applicationDeploymentName = "aurora",
            namespace = "aurora-1",
            affiliation = "aurora",
            dockerImageRepo = null,
            releaseTo = "releaseTo",
            time = Instant.EPOCH
        ),
        metric = Id("application_status", Tags.of(Tag.of("foo", "bar")), null, null, Type.GAUGE)
    )
    val app1v2 = app1v1.copy(publicData = app1v1.publicData.copy(deployTag = "prod"))

    val sourceApplicationDataService = mock(ApplicationDataServiceOpenShift::class.java)
    val openshiftService = mock(OpenShiftService::class.java)
    val meterRegisy = mock(MeterRegistry::class.java)
    val applicationDataService =
        ApplicationDataServiceCacheDecorator(sourceApplicationDataService, openshiftService, "aurora", 1L, meterRegisy)

    val affiliation = "aurora"
    val envs = listOf(Environment("aurora-1", affiliation))
    val affiliationEnvs = mapOf(affiliation to envs)

    val affiliations = listOf(affiliation)

    @Test
    fun `should update cache from OpenShiftApplicationDataService`() {

        given(sourceApplicationDataService.findAndGroupAffiliations(affiliations)).willReturn(affiliationEnvs)
        given(sourceApplicationDataService.findAllApplicationDataForEnv(envs))
            .willReturn(listOf(app1v1))
            .willReturn(listOf(app1v2))

        given(openshiftService.projectsForUser()).willReturn(setOf(project()))

        applicationDataService.refreshCache(affiliations)
        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isEqualTo(app1v1)

        applicationDataService.refreshCache(affiliations)
        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isEqualTo(app1v2)
    }

    @Test
    fun `should return empty response if current user has no access`() {
        given(sourceApplicationDataService.findAndGroupAffiliations(affiliations)).willReturn(affiliationEnvs)
        given(sourceApplicationDataService.findAllApplicationDataForEnv(envs))
            .willReturn(listOf(app1v1))

        applicationDataService.refreshCache(affiliations)
        given(openshiftService.projectByNamespaceForUser(app1v1.namespace)).willReturn(project())
        assertThat(applicationDataService.findApplicationDataByApplicationDeploymentId(app1Id)).isNull()
    }

    @Test
    fun `should skip application if we do not have access`() {

        given(sourceApplicationDataService.findAndGroupAffiliations(affiliations)).willReturn(affiliationEnvs)
        given(sourceApplicationDataService.findAllApplicationDataForEnv(envs))
            .willReturn(listOf(app1v1))

        applicationDataService.refreshCache(affiliations)
        given(openshiftService.projectsForUser()).willReturn(emptySet())
        assertThat(applicationDataService.findAllApplicationData(affiliations)).isEmpty()
    }

    fun project() = Project().apply {

        metadata = ObjectMeta().apply {
            namespace = app1v1.namespace
            name = app1v1.namespace
        }
    }
}