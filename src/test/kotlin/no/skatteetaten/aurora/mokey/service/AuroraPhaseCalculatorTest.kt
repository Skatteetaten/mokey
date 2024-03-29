package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fkorotkov.kubernetes.apps.newDeploymentCondition
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class AuroraPhaseCalculatorTest {
    /*
      Denne viser scaling timeout nå, men bør ikke være det
    {
        "lastTransitionTime": "2019-10-18T10:25:12Z",
        "lastUpdateTime": "2019-10-18T10:25:12Z",
        "message": "Deployment config does not have minimum availability.",
        "status": "False",
        "type": "Available"
    },
    {
        "lastTransitionTime": "2020-02-06T16:01:11Z",
        "lastUpdateTime": "2020-02-06T16:01:11Z",
        "message": "replication controller \"testdatagenerator-73\" has failed progressing",
        "reason": "ProgressDeadlineExceeded",
        "status": "False",
        "type": "Progressing"
    }
     */

    /*
   venter på første apply
 {
                "lastTransitionTime": "2020-03-25T20:53:03Z",
                "lastUpdateTime": "2020-03-25T20:53:03Z",
                "message": "Deployment config does not have minimum availability.",
                "status": "False",
                "type": "Available"
            },

    {
                "lastTransitionTime": "2020-03-25T20:53:28Z",
                "lastUpdateTime": "2020-03-25T20:53:28Z",
                "message": "replication controller \"aos-4314-mokey-1\" is waiting for pod \"aos-4314-mokey-1-deploy\" to run",
                "status": "Unknown",
                "type": "Progressing"
            }

     */
    @Test
    fun `should be complete if available and not deploying`() {
        val now = Instant.EPOCH
        val limit = Duration.ofMinutes(1L)
        val conditions = listOf(
            createAvailableCondition(now),
            createProgressingCondition(now)
        )

        assertThat(conditions.findPhase(limit, now)).isEqualTo("Complete")
    }

    @Test
    fun `should be nodeploy if no deploy made yet`() {
        val now = Instant.EPOCH
        val limit = Duration.ofMinutes(1L)
        val conditions = listOf(
            createAvailableCondition(now, true)
        )

        assertThat(conditions.findPhase(limit, now)).isEqualTo(null)
    }

    @Test
    fun `should be deploy failed if last deployment failed`() {
        val now = Instant.EPOCH
        val limit = Duration.ofMinutes(1L)
        val conditions = listOf(
            createAvailableCondition(now),
            createProgressingCondition(now, "Failed")
        )

        assertThat(conditions.findPhase(limit, now)).isEqualTo("Failed")
    }

    @Test
    fun `should be ongoing of deploy is ongoing`() {
        val now = Instant.EPOCH
        val limit = Duration.ofMinutes(1L)
        val conditions = listOf(
            createAvailableCondition(now, true),
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
            createProgressingCondition(now)
        )

        assertThat(conditions.findPhase(limit, twoMinutesAfter)).isEqualTo("Complete")
    }

    @Test
    fun `should be failed if still scaling after 1 minute and last deploy failed`() {
        val now = Instant.EPOCH
        val twoMinutesAfter = now + Duration.ofMinutes(2L)
        val limit = Duration.ofMinutes(1L)
        val conditions = listOf(
            createAvailableCondition(now, true),
            createProgressingCondition(now, "Failed")
        )

        assertThat(conditions.findPhase(limit, twoMinutesAfter)).isEqualTo("Failed")
    }

    @Test
    fun `should be scaling if scaling within limit`() {
        val now = Instant.EPOCH
        val oneMinuteAfter = now + Duration.ofMinutes(1L)
        val limit = Duration.ofMinutes(2L)
        val conditions = listOf(
            createAvailableCondition(now, true),
            createProgressingCondition(now)
        )

        assertThat(conditions.findPhase(limit, oneMinuteAfter)).isEqualTo("Scaling")
    }

    private fun createAvailableCondition(
        time: Instant,
        ongoing: Boolean = false
    ): DeploymentCondition = newDeploymentCondition {
        lastTransitionTime = time.toString()
        lastUpdateTime = time.toString()

        when {
            ongoing -> {
                message = "Deployment config does not have minimum availability."
                status = "False"
            }
            else -> {
                message = "Deployment config has minimum availability"
                status = "True"
            }
        }

        type = "Available"
    }

    private fun createProgressingCondition(
        time: Instant,
        mode: String = "Complete"
    ): DeploymentCondition = newDeploymentCondition {
        lastTransitionTime = time.toString()
        lastUpdateTime = time.toString()

        when (mode) {
            "Ongoing" -> {
                message = "ReplicationController \"test-1\" is progressing."
                reason = "ReplicationControllerUpdated"
                status = "True"
            }
            "Failed" -> {
                reason = "ProgressDeadlineExceeded"
                message = "ReplicationController \"test-1\" has timed out progressing."
                status = "False"
            }
            else -> {
                message = "replication controller \"test-1\" successfully rolled out"
                reason = "NewReplicationControllerAvailable"
                status = "True"
            }
        }

        type = "Progressing"
    }
}
