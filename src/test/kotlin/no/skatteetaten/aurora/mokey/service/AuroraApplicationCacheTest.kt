package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfigBuilder
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuroraApplicationCacheTest {
    private val openShiftService = mockk<OpenShiftService>()
    private val applicationService = mockk<AuroraApplicationService>()

    private val service = AuroraApplicationCacheService(openShiftService, applicationService)

    private val project = "jedi"
    private val name = "yoda"

    private val dc = DeploymentConfigBuilder()
            .withNewMetadata().withName(name).withNamespace(project).endMetadata()
            .build()

    private val app = AuroraApplication(name, project)

    @Test
    fun `should scrape deploymentConfigs and add cache`() {
        every { openShiftService.deploymentConfigs(project) } returns listOf(dc)
        every { applicationService.handleApplication(project, dc) } returns app

        service.load(listOf(project))

        assertTrue(service.cachePopulated)
        assertTrue(service.cachePopulated)
        assertEquals(1, service.cache.size)
        assertEquals(app, service.get("$project/$name"))
    }

    @Test
    fun `should remove old applications when scraping for a second time`() {
        val name2 = "anakin"
        val project2 = "sith"
        val dc2 = DeploymentConfigBuilder()
                .withNewMetadata().withName(name2).withNamespace(project2).endMetadata()
                .build()

        val app2 = AuroraApplication(name2, project2)

        every { openShiftService.deploymentConfigs(project) } returns listOf(dc)
        every { applicationService.handleApplication(project, dc) } returns app
        every { openShiftService.deploymentConfigs(project2) } returns listOf(dc2)
        every { applicationService.handleApplication(project2, dc2) } returns app2

        service.load(listOf(project))
        service.load(listOf(project2))

        assertTrue(service.cachePopulated)
        assertEquals(1, service.cache.size)
        assertEquals(app2, service.get("$project2/$name2"))
    }

}