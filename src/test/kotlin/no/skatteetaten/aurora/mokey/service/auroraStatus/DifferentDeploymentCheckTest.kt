package no.skatteetaten.aurora.mokey.service.auroraStatus

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import org.junit.jupiter.api.Test
import java.time.Instant.now
import java.time.temporal.ChronoUnit

class DifferentDeploymentCheckTest {
    private val differentDeploymentCheck = DifferentDeploymentCheck(20L)
    @Test
    fun `pods are not failing if startTime is null for both`() {
        val podDetails = PodDetailsDataBuilder(deployment = "deployment1", startTime = null)
        val isFailing = differentDeploymentCheck.isFailing(
            mockk(),
            listOf(
                podDetails.build(),
                podDetails.copy(deployment = "deployment2").build(),
            ),
            now(),
        )

        assertThat(isFailing).isFalse()
    }
    @Test
    fun `pods are failing when startTime is before now minus threshold for both pods`() {
        val podDetails = PodDetailsDataBuilder(deployment = "deployment1", startTime = now().minus(1, ChronoUnit.DAYS))
        val isFailing = differentDeploymentCheck.isFailing(
            mockk(),
            listOf(
                podDetails.build(),
                podDetails.copy(deployment = "deployment2").build(),
            ),
            now(),
        )

        assertThat(isFailing).isTrue()
    }
    @Test
    fun `pods are not failing when startTime is null for one pod and startTime is after threshold for the other pod`() {
        val podDetails1 = PodDetailsDataBuilder(deployment = "deployment1", startTime = null)
        val podDetails2 = PodDetailsDataBuilder(deployment = "deployment2", startTime = now())
        val isFailing = differentDeploymentCheck.isFailing(
            mockk(),
            listOf(
                podDetails2.build(),
                podDetails1.build(),
            ),
            now(),
        )

        assertThat(isFailing).isFalse()
    }
}
