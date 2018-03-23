package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.PodDataBuilder
import no.skatteetaten.aurora.mokey.model.ManagementData
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
    fun `get pod details`() {
        val dc = DeploymentConfigDataBuilder().build()
        val pod = PodDataBuilder().build()
        every { openShiftService.pods("namespace", emptyMap()) } returns listOf(pod)
        every { managementDataService.load("127.0.0.1", "/management-path") } returns ManagementData()

        val podDetailsList = podService.getPodDetails(dc)

        assert(podDetailsList).hasSize(1)
        val podDetails = podDetailsList[0]
        assert(podDetails.openShiftPodExcerpt.name).isEqualTo("name")
    }
}