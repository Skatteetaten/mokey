package no.skatteetaten.aurora.mokey.service.auroraStatus

import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.DOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OBSERVE
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.StatusCheck
import no.skatteetaten.aurora.mokey.model.StatusDescription
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.Instant.parse
import java.time.format.DateTimeParseException

private val logger = KotlinLogging.logger {}

val downStatuses = listOf("OUT_OF_SERVICE", "DOWN")
val upStatuses = listOf("UP", "COMMENT")

@Component
class PodNotReadyCheck(@Value("\${mokey.status.notready.duration:10m}") val notReadyDuration: Duration) : StatusCheck(
    StatusDescription(
        ok = "All containers in pod are ready.",
        failed = "One or more container in pod is not ready for more then $notReadyDuration"
    ),
    OBSERVE
) {
    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean {

        fun notReadyOverThreshold(pod: OpenShiftPodExcerpt): Boolean {
            if (pod.startTime == null) {
                return false
            }
            try {
                val started = Instant.parse(pod.startTime)
                val duration = Duration.between(started, time)
                if (duration < notReadyDuration) {
                    return false
                }
            } catch (e: DateTimeParseException) {
                logger.warn("Kunne ikke parse startTime fra pod=${pod.name} value=${pod.startTime} message=${e.localizedMessage}")
                return false
            }

            return pod.containers.any { !it.ready }
        }

        return pods.any { notReadyOverThreshold(it.openShiftPodExcerpt) }
    }
}

@Component
class AverageRestartErrorCheck(@Value("\${mokey.status.restart.error:100}") val averageRestartErrorThreshold: Int) :
    StatusCheck(
        StatusDescription(
            ok = "Average restarts for pods is below $averageRestartErrorThreshold.",
            failed = "Average restarts for pods is above $averageRestartErrorThreshold."
        ),
        DOWN
    ) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        pods.isRestartsAboveThreshold(averageRestartErrorThreshold)
}

@Component
class AnyPodDownCheck :
    StatusCheck(
        StatusDescription(
            ok = "No pods responded with DOWN status from management health endpoint.",
            failed = "One or more pods responded with DOWN status from management health endpoint."
        ),
        DOWN
    ) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant) =
        pods.validateStatus { downStatuses.contains(it) }
}

@Component
class NoAvailablePodsCheck : StatusCheck(
    StatusDescription(
        ok = "There is the right amount of available pods.",
        failed = "Expected running pods, but there are none."
    ), DOWN
) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.targetReplicas > app.availableReplicas && app.availableReplicas == 0
}

@Component
class DifferentDeploymentCheck(@Value("\${mokey.status.differentdeployment.hour:20}") val hourThreshold: Long) :
    StatusCheck(
        StatusDescription(
            ok = "All pods have the same DeploymentConfig.",
            failed = "There have been pods with different DeploymentConfigs for more than $hourThreshold hours."
        ), DOWN
    ) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean {
        val threshold = time.minus(Duration.ofHours(hourThreshold))
        val numberOfDifferentDeployments = pods.map { it.openShiftPodExcerpt.replicaName }.distinct().count()
        return when {
            pods.size < 2 -> false
            numberOfDifferentDeployments == 1 -> false
            else -> pods.any { p ->
                if (p.openShiftPodExcerpt.startTime.isNullOrEmpty()) {
                    logger.info { "pod startTime is null for ${p.openShiftPodExcerpt.name}" }
                    false
                } else {
                    try {
                        parse(p.openShiftPodExcerpt.startTime).isBefore(threshold)
                    } catch (e: DateTimeParseException) {
                        logger.warn { "could not parse ${p.openShiftPodExcerpt.startTime}" }
                        false
                    }
                }
            }
        }
    }
}

@Component
class AverageRestartObserveCheck(@Value("\${mokey.status.restart.observe:20}") val averageRestartObserveThreshold: Int) :
    StatusCheck(
        StatusDescription(
            ok = "Average restarts for pods is below $averageRestartObserveThreshold.",
            failed = "Average restarts for pods is above $averageRestartObserveThreshold."
        ), OBSERVE
    ) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        pods.isRestartsAboveThreshold(averageRestartObserveThreshold)
}

@Component
class AnyPodObserveCheck :
    StatusCheck(
        StatusDescription(
            ok = "No pods responded with OBSERVE status from management health endpoint.",
            failed = "One or more pods responded with OBSERVE status from management health endpoint."
        ), OBSERVE
    ) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant) =
        pods.validateStatus { !downStatuses.contains(it) && !upStatuses.contains(it) }
}

@Component
class DeployFailedCheck : StatusCheck(
    StatusDescription(
        ok = "Last deployment was successful.",
        failed = "Last deployment failed."
    ), OBSERVE
) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.lastDeployment == "failed"
}

@Component
class TooManyPodsCheck : StatusCheck(
    StatusDescription(
        ok = "There are not too many pods.",
        failed = "There are more pods than expected."
    ), OBSERVE
) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.targetReplicas < pods.size
}

@Component
class TooFewPodsCheck : StatusCheck(
    StatusDescription(
        ok = "There are not too few pods.",
        failed = "There are less pods than expected."
    ), OBSERVE
) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.targetReplicas > pods.size
}

@Component
class NoDeploymentCheck : StatusCheck(
    StatusDescription(
        ok = "At least one deployment has been made.",
        failed = "There have not been any deployments yet."
    ), OFF
) {
    override val isOverridingAuroraStatus = true

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.lastDeployment == null
}

@Component
class OffCheck : StatusCheck(
    StatusDescription(
        ok = "Deployment has not been turned off.",
        failed = "Deployment has been turned off."
    ), OFF
) {
    override val isOverridingAuroraStatus = true

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        app.targetReplicas == 0 && app.availableReplicas == 0
}

@Component
class DeploymentInProgressCheck :
    StatusCheck(
        StatusDescription(
            ok = "No new deployment is in progress.",
            failed = "A new deployment is in progress, other statuses are ignored."
        ), HEALTHY
    ) {
    override val isOverridingAuroraStatus = true

    val finalPhases = listOf("complete", "failed", null)

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean =
        !finalPhases.contains(app.lastDeployment)
}

@Component
class ApplicationScaledDownCheck : StatusCheck(
    StatusDescription(
        ok = "Application is not scaled down.",
        failedFn = { context -> "Application is scaled down by Destructor reason=${context["reason"]}. Email has been sent to the creator" }
    ), OBSERVE
) {

    override fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean = app.scaledDown != null
    override fun generateContext(app: DeployDetails, pods: List<PodDetails>, time: Instant): Map<String, String> {
       return app.scaledDown?.let {
            mapOf("reason" to it)
        } ?: emptyMap()
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
            val status = it.at("/status").textValue().toUpperCase()
            validator(status)
        } ?: false
    }
}
