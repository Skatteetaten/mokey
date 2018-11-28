package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.AuroraApplicationDeploymentDataBuilder
import no.skatteetaten.aurora.mokey.ContainerStatusBuilder
import no.skatteetaten.aurora.mokey.ContainerStatuses
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ManagementDataBuilder
import no.skatteetaten.aurora.mokey.PodDataBuilder
import no.skatteetaten.aurora.mokey.model.DeployDetails
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

    val deployDetails = DeployDetails(1, 1)

    @Test
    fun `should collect pods only for current dc in current namespace`() {
        val builder = AuroraApplicationDeploymentDataBuilder()
        every { openShiftService.pods(builder.appNamespace, mapOf("name" to builder.appName)) } returns listOf()
        val podDetails = podService.getPodDetails(builder.build(), deployDetails)
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

        val podDetails = podService.getPodDetails(appBuilder.build(), deployDetails)

        assert(podDetails).hasSize(1)
        assert(podDetails[0].openShiftPodExcerpt.podIP).isEqualTo(podBuilder.ip)
        assert(podDetails[0].managementData).isEqualTo(managementResult)
    }

    @Test
    fun `verify that PodDetails is created from Pod and ManagementData`() {
        val managementData = ManagementDataBuilder().build()
        val podDataBuilder = PodDataBuilder()

        val podDetails = PodService.createPodDetails(podDataBuilder.build(), managementData, deployDetails)

        assert(podDetails.openShiftPodExcerpt.name).isEqualTo(podDataBuilder.podName)
        assert(podDetails.managementData).isEqualTo(managementData)
        assert(podDetails.openShiftPodExcerpt.phase).isEqualTo("phase")
        assert(podDetails.openShiftPodExcerpt.containers.first().state).isEqualTo("running")
    }

    @Test
    fun `verify that waiting container is created from Pod and ManagementData`() {
        val managementData = ManagementDataBuilder().build()
        val podDataBuilder = PodDataBuilder(
            containerList = listOf(
                ContainerStatusBuilder(containerStatus = ContainerStatuses.WAITING).build()
            )
        )

        val podDetails = PodService.createPodDetails(podDataBuilder.build(), managementData, deployDetails)

        assert(podDetails.openShiftPodExcerpt.name).isEqualTo(podDataBuilder.podName)
        assert(podDetails.managementData).isEqualTo(managementData)
        assert(podDetails.openShiftPodExcerpt.containers.first().state).isEqualTo("waiting")
    }

    @Test
    fun `verify that terminating container is created from Pod and ManagementData`() {
        val managementData = ManagementDataBuilder().build()
        val podDataBuilder = PodDataBuilder(
            containerList = listOf(
                ContainerStatusBuilder(containerStatus = ContainerStatuses.TERMINATED).build()
            )
        )

        val podDetails = PodService.createPodDetails(podDataBuilder.build(), managementData, deployDetails)

        assert(podDetails.openShiftPodExcerpt.name).isEqualTo(podDataBuilder.podName)
        assert(podDetails.managementData).isEqualTo(managementData)
        assert(podDetails.openShiftPodExcerpt.containers.first().state).isEqualTo("terminated")
    }
}