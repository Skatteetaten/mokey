package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.OpenShiftContainerExcerpt
import no.skatteetaten.aurora.mokey.service.auroraStatus.PodNotReadyCheck
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.Duration.ofMinutes
import java.time.Instant
import java.time.Instant.EPOCH

val testExcerpt = OpenShiftContainerExcerpt(
    name = "name-java",
    state = "running",
    image = "docker....",
    restartCount = 0,
    ready = true,
)

val now: Instant = EPOCH + ofMinutes(10)

class PodNotReadyCheckTest {
    private val app = DeployDetails(
        targetReplicas = 1,
        availableReplicas = 1,
        deployment = "foo-1",
        phase = "Running",
        deployTag = "1",
    )
    private val check = PodNotReadyCheck(ofMinutes(5))

    enum class PodNotReadyCheckDataSource(
        val triggered: Boolean = true,
        val containers: List<OpenShiftContainerExcerpt>,
        val started: Instant? = EPOCH,
    ) {
        SINGLE_READY(triggered = false, containers = listOf(testExcerpt)),
        SINGLE_UNREADY(triggered = true, containers = listOf(testExcerpt.copy(ready = false))),
        JUST_STARTED(
            triggered = false,
            containers = listOf(testExcerpt.copy(ready = false)),
            started = now - Duration.ofSeconds(5)
        ),
        MULTIPLE_ONE_UNREADY(triggered = true, containers = listOf(testExcerpt, testExcerpt.copy(ready = false))),
        NO_STARTED_TIME(triggered = false, containers = listOf(testExcerpt), started = null),
    }

    @ParameterizedTest
    @EnumSource(PodNotReadyCheckDataSource::class)
    fun `pod not ready check`(test: PodNotReadyCheckDataSource) {
        val pods = listOf(PodDetailsDataBuilder(startTime = test.started, containers = test.containers).build())
        val status = check.isFailing(app, pods, now)

        assertThat(status).isEqualTo(test.triggered)
    }

    @Test
    fun `should handle unparsable date`() {
        val pods = listOf(
            PodDetailsDataBuilder(
                startTime = null,
                startTimeString = "foobar",
                containers = listOf(testExcerpt)
            ).build()
        )
        val status = check.isFailing(app, pods, now)

        assertThat(status).isEqualTo(false)
    }
}
