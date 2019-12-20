package no.skatteetaten.aurora.mokey.service.auroraStatus

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.mockk
import java.time.Instant
import java.time.temporal.ChronoUnit
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import org.junit.jupiter.api.Test

class DifferentDeploymentCheckTest {
    private val differentDeploymentCheck = DifferentDeploymentCheck(20L)
    @Test
    fun `pod is not failing if startTime is null`() {
        val podDetails = PodDetailsDataBuilder(deployment = "deployment1", startTime = null)
        val isFailing = differentDeploymentCheck.isFailing(mockk(), listOf(podDetails.build(), podDetails.copy(deployment = "deployment2").build()), Instant.now())
        assertThat(isFailing).isFalse()
    }
    @Test
    fun `pod is failing when startTime is before now minus threshold`() {
        val podDetails = PodDetailsDataBuilder(deployment = "deployment1", startTime = Instant.now().minus(1, ChronoUnit.DAYS))
        val isFailing = differentDeploymentCheck.isFailing(mockk(), listOf(podDetails.build(), podDetails.copy(deployment = "deployment2").build()), Instant.now())
        assertThat(isFailing).isTrue()
    }
}
