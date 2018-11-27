package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.DOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OBSERVE
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.HealthStatusDetail
import no.skatteetaten.aurora.mokey.model.PodDetails
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class AuroraStatusCalculator(
    @Value("\${mokey.status.restart.observe:20}") val avergageRestartObserveThreshold: Int,
    @Value("\${mokey.status.restart.error:100}") val avergageRestartErrorThreshold: Int,
    @Value("\${mokey.status.differentdeployment.hour:2}") val differentDeploymentHourThreshold: Long
) {

    val DEPLOYMENT_IN_PROGRESS = "DEPLOYMENT_IN_PROGRESS"

    fun calculateStatus(app: DeployDetails, pods: List<PodDetails>, time: Instant = Instant.now()): AuroraStatus {

        val lastDeployment = app.phase?.toLowerCase()
        val availableReplicas = app.availableReplicas
        val targetReplicas = app.targetReplicas

        val healthStatuses = mutableSetOf<HealthStatusDetail>()

        val threshold = time.minus(Duration.ofHours(differentDeploymentHourThreshold))
        if (hasOldPodsWithDifferentDeployments(pods, threshold)) {
            healthStatuses.add(HealthStatusDetail(DOWN, "DIFFERENT_DEPLOYMENTS"))
        }

        if (targetReplicas > availableReplicas && availableReplicas == 0) {
            healthStatuses.add(HealthStatusDetail(DOWN, "NO_AVAILABLE_PODS"))
        }

        if (lastDeployment == null) {
            healthStatuses.add(HealthStatusDetail(OFF, "NO_DEPLOYMENT"))
        } else if (lastDeployment == "failed") {
            if (availableReplicas <= 0) {
                healthStatuses.add(HealthStatusDetail(DOWN, "DEPLOY_FAILED_NO_PODS"))
            } else {
                healthStatuses.add(HealthStatusDetail(OBSERVE, "DEPLOY_FAILED"))
            }
        } else if (lastDeployment != "complete") {
            healthStatuses.add(HealthStatusDetail(HEALTHY, DEPLOYMENT_IN_PROGRESS))
        }

        val averageRestarts = findAverageRestarts(pods)

        if (averageRestarts > avergageRestartErrorThreshold) {
            healthStatuses.add(
                HealthStatusDetail(
                    DOWN,
                    "AVERAGE_RESTART_ABOVE_THRESHOLD"
                )
            )
        } else if (averageRestarts > avergageRestartObserveThreshold) {
            healthStatuses.add(
                HealthStatusDetail(
                    OBSERVE,
                    "AVERAGE_RESTART_ABOVE_THRESHOLD"
                )
            )
        }

        if (targetReplicas < availableReplicas) {
            healthStatuses.add(HealthStatusDetail(OBSERVE, "TOO_MANY_PODS"))
        }

        if (targetReplicas > availableReplicas && availableReplicas != 0) {
            healthStatuses.add(HealthStatusDetail(OBSERVE, "TOO_FEW_PODS"))
        }

        if (targetReplicas == 0 && availableReplicas == 0) {
            healthStatuses.add(HealthStatusDetail(OFF, "OFF"))
        }

        if (lastDeployment == null) {
            healthStatuses.add(HealthStatusDetail(OFF, "NO_DEPLOYMENT"))
        }

        healthStatuses.addAll(findPodStatuses(pods))

        return calculateStatus(healthStatuses)
    }

    fun findPodStatuses(pods: List<PodDetails>): List<HealthStatusDetail> {
        return pods.mapNotNull { pod ->
            pod.managementData.health?.deserialized?.let {
                val statusLevel = fromApplicationStatus(it.status.name)
                val podName = pod.openShiftPodExcerpt.name
                HealthStatusDetail(statusLevel, "POD_HEALTH_CHECK", podName)
            }
        }
    }

    fun fromApplicationStatus(status: String): AuroraStatusLevel {
        return when (status.toUpperCase()) {
            "UP" -> AuroraStatusLevel.HEALTHY
            "OBSERVE" -> AuroraStatusLevel.OBSERVE
            "COMMENT" -> AuroraStatusLevel.OBSERVE
            "OUT_OF_SERVICE" -> AuroraStatusLevel.DOWN
            "DOWN" -> AuroraStatusLevel.DOWN
            else -> AuroraStatusLevel.OBSERVE
        }
    }

    fun findAverageRestarts(ap: List<PodDetails>): Int {
        if (ap.isEmpty()) {
            return 0
        }

        val totalRestarts = ap.sumBy { it.openShiftPodExcerpt.containers.sumBy { c -> c.restartCount } }
        return totalRestarts / ap.size
    }

    fun hasOldPodsWithDifferentDeployments(ap: List<PodDetails>, threshold: Instant): Boolean {
        if (ap.size < 2) {
            return false
        }

        val numberOfDifferentDeployments = ap.map { it.openShiftPodExcerpt.replicaName }.distinct().count()
        if (numberOfDifferentDeployments == 1) {
            return false
        }

        return ap.stream().anyMatch { p -> Instant.parse(p.openShiftPodExcerpt.startTime).isBefore(threshold) }
    }

    private fun calculateStatus(healthStatuses: Set<HealthStatusDetail>): AuroraStatus {

        val getMostCriticalStatus = { acc: HealthStatusDetail, auroraStatus: HealthStatusDetail ->
            if (auroraStatus.level.level > acc.level.level) auroraStatus else acc
        }

        if (healthStatuses.isEmpty()) {
            return AuroraStatus(HEALTHY, statuses = setOf(HealthStatusDetail(HEALTHY)))
        }
        healthStatuses.find { it.comment == DEPLOYMENT_IN_PROGRESS }?.let {
            return AuroraStatus(it.level, it.comment, healthStatuses)
        }

        return healthStatuses.reduce(getMostCriticalStatus).let {
            AuroraStatus(it.level, it.comment, healthStatuses)
        }
    }
}