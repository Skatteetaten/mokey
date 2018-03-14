package no.skatteetaten.aurora.mokey.health

import io.fabric8.openshift.api.model.DeploymentConfigBuilder
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import no.skatteetaten.aurora.mokey.service.AuroraApplicationService
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.health.Status.DOWN
import org.springframework.boot.actuate.health.Status.UP

class CrawlRunHealthCheckTest {
    private val openShiftService = mockk<OpenShiftService>()
    private val applicationService = mockk<AuroraApplicationService>()

    private val service = AuroraApplicationCacheService(openShiftService, applicationService)

    private val healthCheck = CrawlRunHealthCheck(service)

    private val project = "jedi"
    private val name = "yoda"

    private val dc = DeploymentConfigBuilder()
            .withNewMetadata().withName(name).withNamespace(project).endMetadata()
            .build()

    private val app = AuroraApplication(name, project)

    @Test
    fun `should scrape and register healthCheck`() {
        every { openShiftService.deploymentConfigs(project) } returns listOf(dc)
        every { applicationService.handleApplication(project, dc) } returns app


        assertEquals(healthCheck.health().status, DOWN)

        service.load(listOf(project))

        Assertions.assertTrue(service.cachePopulated)
        assertEquals(healthCheck.health().status, UP)
    }
}