package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.DOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OBSERVE
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.UNKNOWN
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.PodDetails
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class AuroraStatusCalculator {

    val AVERAGE_RESTART_OBSERVE_THRESHOLD = 20
    val AVERAGE_RESTART_ERROR_THRESHOLD = 100
    val DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD = 2

    fun calculateStatus(deployDetails: DeployDetails?, pods: List<PodDetails>): AuroraStatus {

        if (deployDetails == null) {
            return AuroraStatus(OFF, "NO_DEPLOYMENT")
        }

        val lastDeployment = deployDetails.deploymentPhase
        val availableReplicas = deployDetails.availableReplicas
        val targetReplicas = deployDetails.targetReplicas

        if ("Failed".equals(lastDeployment, ignoreCase = true) && availableReplicas <= 0) {
            return AuroraStatus(DOWN, "DEPLOY_FAILED_NO_PODS")
        }

        if ("Failed".equals(lastDeployment, ignoreCase = true)) {
            return AuroraStatus(OBSERVE, "DEPLOY_FAILED")
        }

        if (lastDeployment == null) {
            return AuroraStatus(OFF, "NO_DEPLOYMENT")
        }

        if (!"Complete".equals(lastDeployment, ignoreCase = true)) {
            return AuroraStatus(HEALTHY, "DEPLOYMENT_IN_PROGRESS")
        }

        if (targetReplicas > availableReplicas && availableReplicas == 0) {
            return AuroraStatus(DOWN, "")
        }

        val threshold = Instant.now().minus(Duration.ofHours(DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD.toLong()))
        if (hasOldPodsWithDifferentDeployments(pods, threshold)) {

            return AuroraStatus(DOWN, "DIFFERENT_DEPLOYMENTS")
        }

        val averageRestarts = findAverageRestarts(pods)

        if (averageRestarts > AVERAGE_RESTART_ERROR_THRESHOLD) {
            return AuroraStatus(DOWN, "AVERAGE_RESTART_ABOVE_THRESHOLD")
        }

        if (averageRestarts > AVERAGE_RESTART_OBSERVE_THRESHOLD) {
            return AuroraStatus(OBSERVE, "AVERAGE_RESTART_ABOVE_THRESHOLD")
        }

        if (targetReplicas > availableReplicas && availableReplicas != 0) {
            return AuroraStatus(OBSERVE, "TOO_MANY_PODS")
        }


        if (targetReplicas == 0 && availableReplicas == 0) {
            return AuroraStatus(OFF, "OFF")
        }

        if (targetReplicas < availableReplicas) {
            return AuroraStatus(OBSERVE, "TOO_FEW_PODS")
        }

        //TODO: Hva gjør hvis hvis denne er Unknown, vi ikke får svar?
        val podStatus = findPodStatus(pods)

        if (podStatus != HEALTHY) {
            return AuroraStatus(podStatus, "POD_HEALTH_CHECK")
        }

        if (availableReplicas == targetReplicas && availableReplicas != 0) {
            return AuroraStatus(HEALTHY, "")
        }

        return AuroraStatus(UNKNOWN, "")
    }

    fun fromApplicationStatus(status: String): AuroraStatusLevel {
        return when (status.toUpperCase()) {
            "UP" -> AuroraStatusLevel.HEALTHY
            "OBSERVE" -> AuroraStatusLevel.OBSERVE
            "COMMENT" -> AuroraStatusLevel.OBSERVE
            "UNKNOWN" -> AuroraStatusLevel.UNKNOWN
            "OUT_OF_SERVICE" -> AuroraStatusLevel.DOWN
            "DOWN" -> AuroraStatusLevel.DOWN
            else -> AuroraStatusLevel.UNKNOWN
        }
    }

    fun findPodStatus(pods: List<PodDetails>): AuroraStatusLevel {
        // TODO: Health endpoint
        return HEALTHY
//        return pods.map { fromApplicationStatus(it.openShiftPodExcerpt.status) }.toSortedSet().firstOrNull() ?: HEALTHY
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