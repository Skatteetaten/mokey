package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.DOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OBSERVE
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.utils.value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

data class AuroraStatuses(
    val deploymentStatuses: List<AuroraStatus>,
    val podStatuses: Map<String, AuroraStatus>
) {
    val currentStatus: AuroraStatus
        get() = findMostCriticalStatus()

    private fun findMostCriticalStatus(): AuroraStatus {

        val getMostCriticalStatus = { auroraStatus: AuroraStatus, acc: AuroraStatus ->
            if (acc.level.level > auroraStatus.level.level) acc else auroraStatus
        }

        val nonHealthyPodStatuses = podStatuses.values.toList().filter { it.level != HEALTHY }
        val allStatuses = nonHealthyPodStatuses + deploymentStatuses

        return if (allStatuses.isEmpty()) AuroraStatus(HEALTHY, "") else allStatuses.reduceRight(getMostCriticalStatus)
    }
}

@Service
class AuroraStatusCalculator {

    val AVERAGE_RESTART_OBSERVE_THRESHOLD = 20
    val AVERAGE_RESTART_ERROR_THRESHOLD = 100
    val DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD = 2

    fun calculateStatus(app: DeployDetails, pods: List<PodDetails>): AuroraStatuses {

        val lastDeployment = app.deploymentPhase
        val availableReplicas = app.availableReplicas
        val targetReplicas = app.targetReplicas

        val deploymentStatuses = mutableListOf<AuroraStatus>()

        val threshold = Instant.now().minus(Duration.ofHours(DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD.toLong()))
        if (hasOldPodsWithDifferentDeployments(pods, threshold)) {
            deploymentStatuses.add(AuroraStatus(DOWN, "DIFFERENT_DEPLOYMENTS"))
        }

        if (targetReplicas > availableReplicas && availableReplicas == 0) {
            deploymentStatuses.add(AuroraStatus(DOWN, "")) // TODO: Reason, comment?
        }

        if ("Failed".equals(lastDeployment, ignoreCase = true) && availableReplicas <= 0) {
            deploymentStatuses.add(AuroraStatus(DOWN, "DEPLOY_FAILED_NO_PODS"))
        }

        val averageRestarts = findAverageRestarts(pods)

        if (averageRestarts > AVERAGE_RESTART_ERROR_THRESHOLD) {
            deploymentStatuses.add(AuroraStatus(DOWN, "AVERAGE_RESTART_ABOVE_THRESHOLD"))
        }

        if (averageRestarts > AVERAGE_RESTART_OBSERVE_THRESHOLD) {
            deploymentStatuses.add(AuroraStatus(OBSERVE, "AVERAGE_RESTART_ABOVE_THRESHOLD"))
        }

        if ("Failed".equals(lastDeployment, ignoreCase = true)) {
            deploymentStatuses.add(AuroraStatus(OBSERVE, "DEPLOY_FAILED"))
        }

        if (targetReplicas < availableReplicas) {
            deploymentStatuses.add(AuroraStatus(OBSERVE, "TOO_FEW_PODS"))
        }

        if (targetReplicas > availableReplicas && availableReplicas != 0) {
            deploymentStatuses.add(AuroraStatus(OBSERVE, "TOO_MANY_PODS"))
        }

        if (targetReplicas == 0 && availableReplicas == 0) {
            deploymentStatuses.add(AuroraStatus(OFF, "OFF"))
        }

        if (lastDeployment == null) {
            deploymentStatuses.add(AuroraStatus(OFF, "NO_DEPLOYMENT"))
        }

        val podStatuses = findPodStatuses(pods)

        return AuroraStatuses(deploymentStatuses, podStatuses)
    }

    fun findPodStatuses(pods: List<PodDetails>): Map<String, AuroraStatus> {
        return pods.mapNotNull { pod ->
            val healthStatus = pod.managementData.value?.health?.value?.status?.name
            healthStatus?.let {
                val statusLevel = fromApplicationStatus(it)
                val podName = pod.openShiftPodExcerpt.name
                podName to AuroraStatus(statusLevel, "POD_HEALTH_CHECK")
            }
        }.toMap()
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

        val totalRestarts = ap.sumBy { it.openShiftPodExcerpt.restartCount }
        return totalRestarts / ap.size
    }

    fun hasOldPodsWithDifferentDeployments(ap: List<PodDetails>, threshold: Instant): Boolean {
        if (ap.size < 2) {
            return false
        }

        val numberOfDifferentDeployments = ap.map { it.openShiftPodExcerpt.deployment }.distinct().count()
        if (numberOfDifferentDeployments == 1) {
            return false
        }

        return ap.stream().anyMatch { p -> Instant.parse(p.openShiftPodExcerpt.startTime).isBefore(threshold) }
    }
}