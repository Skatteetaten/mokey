package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfigBuilder
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.Environment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuroraApplicationCacheTest {
    private val openShiftService = mockk<OpenShiftService>()
    private val applicationService = mockk<AuroraApplicationService>()

    private val service = AuroraApplicationCacheService(openShiftService, applicationService)

    private val project = "starwars-jedi"
    private val name = "yoda"

    private val dc = DeploymentConfigBuilder()
        .withNewMetadata().withName(name).withNamespace(project).endMetadata()
        .build()

    private val app = ApplicationData(
        applicationId = ApplicationId(name, Environment.fromNamespace(project)),
        name = name,
        namespace = project,
        affiliation = "starwars",
        targetReplicas = 1,
        availableReplicas = 1,
        auroraVersion = "1.0.1-b1.0.0-flange-1.8.0",
        booberDeployId = "roflmao12",
        deploymentPhase = "Completed",
        deployTag = "1",
        managementPath = ":8081/managment"
    )

    @Test
    fun `should scrape deploymentConfigs and add cache`() {
        every { openShiftService.deploymentConfigs(project) } returns listOf(dc)
        every { applicationService.handleApplication(dc) } returns app

        service.load(listOf(project))

        assertEquals(1, service.cache.size)
        assertEquals(app, service.get(app.id.toString()))
    }

    @Test
    fun `should remove old applications when scraping for a second time`() {
        val name2 = "anakin"
        val project2 = "starwars-sith"
        val dc2 = DeploymentConfigBuilder()
            .withNewMetadata().withName(name2).withNamespace(project2).endMetadata()
            .build()

        val app2 = app.copy(name=name2, namespace = project2)

        every { openShiftService.deploymentConfigs(project) } returns listOf(dc)
        every { applicationService.handleApplication(dc) } returns app
        every { openShiftService.deploymentConfigs(project2) } returns listOf(dc2)
        every { applicationService.handleApplication(dc2) } returns app2

        service.load(listOf(project))
        service.load(listOf(project2))

        assertEquals(1, service.cache.size)
        assertEquals(app2, service.get(app2.id.toString()))
    }
}