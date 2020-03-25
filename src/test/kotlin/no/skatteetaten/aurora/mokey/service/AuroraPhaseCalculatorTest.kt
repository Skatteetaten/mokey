package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fkorotkov.openshift.newDeploymentCondition
import io.fabric8.openshift.api.model.DeploymentCondition
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test

class AuroraPhaseCalculatorTest {

    @Test
    fun `should be complete if available and not deploying`() {

        val now = Instant.EPOCH
        val limit = Duration.ofMinutes(1L)

        val conditions = listOf(
            createAvailableCondition(now),
            createProgressingCondition(now)
        )

        assertThat(conditions.findPhase(limit, now)).isEqualTo("Running")
    }

    @Test
    fun `should be nodeploy if no deploy made yet`() {

        val now = Instant.EPOCH
        val limit = Duration.ofMinutes(1L)

        val conditions = listOf(
            createAvailableCondition(now, true)
        )

        assertThat(conditions.findPhase(limit, now)).isEqualTo("NoDeploy")
    }
    @Test
    fun `should be deploy failed if last deployment failed`() {

        val now = Instant.EPOCH
        val limit = Duration.ofMinutes(1L)

        val conditions = listOf(
            createAvailableCondition(now),
            createProgressingCondition(now, "Failed")
        )

        assertThat(conditions.findPhase(limit, now)).isEqualTo("DeployFailed")
    }

    @Test
    fun `should be ongoing of deploy is ongoing`() {

        val now = Instant.EPOCH
        val limit = Duration.ofMinutes(1L)

        val conditions = listOf(
            createAvailableCondition(now),
            createProgressingCondition(now, "Ongoing")
        )

        assertThat(conditions.findPhase(limit, now)).isEqualTo("DeploymentProgressing")
    }
    @Test
    fun `should be failed scaling if still scaling after 1 minute`() {

        val now = Instant.EPOCH
        val twoMinutesAfter = now + Duration.ofMinutes(2L)
        val limit = Duration.ofMinutes(1L)

        val conditions = listOf(
            createAvailableCondition(now, true),
            createProgressingCondition(now, "Failed")
        )

        assertThat(conditions.findPhase(limit, twoMinutesAfter)).isEqualTo("ScalingTimeout")
    }

    @Test
    fun `should be scaling if scaling within limit`() {

        val now = Instant.EPOCH
        val oneMinuteAfter = now + Duration.ofMinutes(1L)
        val limit = Duration.ofMinutes(2L)

        val conditions = listOf(
            createAvailableCondition(now, true),
            createProgressingCondition(now, "Failed")
        )

        assertThat(conditions.findPhase(limit, oneMinuteAfter)).isEqualTo("Scaling")
    }

    private fun createAvailableCondition(time: Instant, ongoing: Boolean = false): DeploymentCondition {
        return newDeploymentCondition {
            lastTransitionTime = time.toString()
            lastUpdateTime = time.toString()
            if (ongoing) {
                message = "Deployment config does not have minimum availability."
                status = "False"
            } else {
                message = "Deployment config has minimum availability"
                status = "True"
            }
            type = "Available"
        }
    }

    private fun createProgressingCondition(time: Instant, mode: String = "Complete"): DeploymentCondition {
        return newDeploymentCondition {
            lastTransitionTime = time.toString()
            lastUpdateTime = time.toString()
            if (mode == "Ongoing") {
                message = "ReplicationController \"test-1\" is progressing."
                reason = "ReplicationControllerUpdated"
                status = "True"
            } else if (mode == "Failed") {
                reason = "ProgressDeadlineExceeded"
                message = "ReplicationController \"test-1\" has timed out progressing."
                status = "False"
            } else {
                message = "replication controller \"test-1\" successfully rolled out"
                reason = "NewReplicationControllerAvailable"
                status = "True"
            }
            type = "Progressing"
        }
    }
}
