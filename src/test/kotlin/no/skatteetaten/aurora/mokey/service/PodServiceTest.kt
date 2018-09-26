package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.AuroraApplicationDeploymentDataBuilder
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ManagementDataBuilder
import no.skatteetaten.aurora.mokey.PodDataBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PodServiceTest {

    private val openShiftService = mockk<OpenShiftService>()
    private val managementDataService = mockk<ManagementDataService>()
    private val podService = PodService(openShiftService, managementDataService)

    @BeforeEach
    fun setUp() {
        clearMocks(openShiftService, managementDataService)
    }

    @Test
    fun `should collect pods only for current dc in current namespace`() {
        val builder = AuroraApplicationDeploymentDataBuilder()
        every { openShiftService.pods(builder.appNamespace, mapOf("name" to builder.appName)) } returns listOf()
        val podDetails = podService.getPodDetails(builder.build())
        assert(podDetails).isEmpty()
    }

    @Test
    fun `should use podIp and management path to get management data and create a PodDetail for each pod`() {
        val dcBuilder = DeploymentConfigDataBuilder()
        val appBuilder = AuroraApplicationDeploymentDataBuilder()
        val podBuilder = PodDataBuilder()
        val managementResult = ManagementDataBuilder().build()
        every { openShiftService.pods(dcBuilder.dcNamespace, dcBuilder.dcSelector) } returns listOf(podBuilder.build())
        every { managementDataService.load(podBuilder.ip, appBuilder.managementPath) } returns managementResult

        val podDetails = podService.getPodDetails(appBuilder.build())

        assert(podDetails).hasSize(1)
        assert(podDetails[0].openShiftPodExcerpt.podIP).isEqualTo(podBuilder.ip)
        assert(podDetails[0].managementData).isEqualTo(managementResult)
    }

    @Test
    fun `verify that PodDetails is created from Pod and ManagementData`() {
        val managementData = ManagementDataBuilder().build()
        val podDataBuilder = PodDataBuilder()

        val podDetails = PodService.createPodDetails(podDataBuilder.build(), managementData)

        assert(podDetails.openShiftPodExcerpt.name).isEqualTo(podDataBuilder.podName)
        assert(podDetails.managementData).isEqualTo(managementData)
        assert(podDetails.openShiftPodExcerpt.status).isEqualTo("reason")
    }
}