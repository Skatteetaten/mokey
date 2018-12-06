package no.skatteetaten.aurora.mokey.service.auroraStatus

import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.DOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OBSERVE
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.StatusCheck
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.Instant.parse

val downStatuses = listOf("OUT_OF_SERVICE", "DOWN")
const val upStatus = "UP"

@Component
class AnyPodObserveCheck :
    StatusCheck("One or more pods responded with OBSERVE status from management health endpoint.", OBSERVE) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant) =
        pods.validateStatus { !downStatuses.contains(it) && it != upStatus }
}

@Component
class AnyPodDownCheck :
    StatusCheck("One or more pods responded with DOWN status from management health endpoint.", DOWN) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant) =
        pods.validateStatus { downStatuses.contains(it) }
}

@Component
class NoAvailablePodsCheck : StatusCheck("Expected running pods, but there are none.", DOWN) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.targetReplicas > app.availableReplicas && app.availableReplicas == 0
}

@Component
class NoDeploymentCheck : StatusCheck("There has not been any deploys yet.", OFF) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.lastDeployment == null
}

@Component
class DeployFailedCheck : StatusCheck("Last deployment failed. There are available pods running.", OBSERVE) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.lastDeployment == "failed" && app.availableReplicas > 0
}

@Component
class DeployFailedNoPodsCheck : StatusCheck("Last deployment failed and there are none available pods.", DOWN) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.lastDeployment == "failed" && app.availableReplicas < 1
}

@Component
class DeploymentInProgressCheck : StatusCheck("A new deployment are in progress.", HEALTHY) {
    val finalPhases = listOf("complete", "failed", null)

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        !finalPhases.contains(app.lastDeployment)
}

@Component
class TooManyPodsCheck : StatusCheck("There are more pods then expected.", OBSERVE) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.targetReplicas < app.availableReplicas
}

@Component
class TooFewPodsCheck : StatusCheck("There are less pods then expected.", OBSERVE) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.targetReplicas > app.availableReplicas && app.availableReplicas != 0
}

@Component
class OffCheck : StatusCheck("Deployment has been turned off.", OFF) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.targetReplicas == 0 && app.availableReplicas == 0
}

@Component
class AverageRestartErrorCheck(@Value("\${mokey.status.restart.error:100}") val averageRestartErrorThreshold: Int) :
    StatusCheck("Average restarts are above $averageRestartErrorThreshold.", DOWN) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        pods.isRestartsAboveThreshold(averageRestartErrorThreshold)
}

@Component
class AverageRestartObserveCheck(@Value("\${mokey.status.restart.observe:20}") val averageRestartObserveThreshold: Int) :
    StatusCheck("Average restarts are above $averageRestartObserveThreshold.", OBSERVE) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        pods.isRestartsAboveThreshold(averageRestartObserveThreshold)
}

@Component
class DifferentDeploymentCheck(@Value("\${mokey.status.differentdeployment.hour:20}") val hourThreshold: Long) :
    StatusCheck("There has been different deployments for more than $hourThreshold hours.", DOWN) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean {
        val threshold = time.minus(Duration.ofHours(hourThreshold))
        val numberOfDifferentDeployments = pods.map { it.openShiftPodExcerpt.replicaName }.distinct().count()
        return when {
            pods.size < 2 -> false
            numberOfDifferentDeployments == 1 -> false
            else -> pods.stream().anyMatch { p -> parse(p.openShiftPodExcerpt.startTime).isBefore(threshold) }
        }
    }
}

fun List<PodDetails>.isRestartsAboveThreshold(threshold: Int): Boolean {
    if (this.isEmpty()) {
        return false
    }
    val totalRestarts = this.sumBy { it.openShiftPodExcerpt.containers.sumBy { c -> c.restartCount } }
    val averageRestarts = totalRestarts / this.size

    return averageRestarts > threshold
}

fun List<PodDetails>.validateStatus(validator: (status: String) -> Boolean): Boolean {
    return this.any { pod ->
        pod.managementData.health?.deserialized?.let {
            val status = it.status.name.toUpperCase()
            validator(status)
        } ?: false
    }
}
