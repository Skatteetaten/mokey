package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.fabric8.kubernetes.api.model.Pod
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ManagementDataBuilder
import no.skatteetaten.aurora.mokey.PodDataBuilder
import no.skatteetaten.aurora.mokey.model.PodDetails
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
        val dc = DeploymentConfigDataBuilder().build()
        every { openShiftService.pods("namespace", mapOf("name" to "name")) } returns listOf()
        podService.getPodDetails(dc)
    }

    @Test
    fun `should use podIp and management path to get management data and create a PodDetail for each pod`() {

        val dc = DeploymentConfigDataBuilder().build()

        every { openShiftService.pods(any(), any()) } returns listOf(PodDataBuilder().build())
        val managementResult = ManagementDataBuilder().build()
        every { managementDataService.load("127.0.0.1", "/management-path") } returns managementResult

        val podDetails = podService.getPodDetails(dc)
        assert(podDetails).hasSize(1)

        assert(podDetails[0].openShiftPodExcerpt.podIP).isEqualTo("127.0.0.1")
        assert(podDetails[0].managementData).isEqualTo(managementResult)
    }

    @Test
    fun `verify that PodDetails is created from Pod and ManagementData`() {

        val managementData = ManagementDataBuilder().build()
        val podDetails = PodService.createPodDetails(PodDataBuilder().build(), managementData)
        assert(podDetails.openShiftPodExcerpt.name).isEqualTo("name")
        assert(podDetails.managementData).isEqualTo(managementData)

        // etc...
    }
}