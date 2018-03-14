package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.model.AuroraApplicationInternal
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatus.AuroraStatusLevel.DOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatus.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.AuroraStatus.AuroraStatusLevel.OBSERVE
import no.skatteetaten.aurora.mokey.model.AuroraStatus.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.AuroraStatus.AuroraStatusLevel.UNKNOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatus.Companion.AVERAGE_RESTART_ERROR_THRESHOLD
import java.time.Duration
import java.time.Instant


object AuroraStatusCalculator {

    fun calculateStatus(app: AuroraApplicationInternal): AuroraStatus {

        val lastDeployment = app.deploymentPhase
        val availableReplicas = app.availableReplicas
        val targetReplicas = app.targetReplicas
        val pods = app.pods
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
            return AuroraStatus(HEALTHY,
                    "DEPLOYMENT_IN_PROGRESS")
        }

        if (targetReplicas > availableReplicas && availableReplicas == 0) {
            return AuroraStatus(DOWN)
        }

        val threshold = Instant.now().minus(Duration.ofHours(AuroraStatus.DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD.toLong()))
        if (hasOldPodsWithDifferentDeployments(pods, threshold)) {

            return AuroraStatus(DOWN, "DIFFERENT_DEPLOYMENTS")
        }

        val averageRestarts = findAverageRestarts(pods)

        if (averageRestarts > AVERAGE_RESTART_ERROR_THRESHOLD) {
            return AuroraStatus(DOWN, "AVERAGE_RESTART_ABOVE_THRESHOLD")
        }

        if (averageRestarts > AuroraStatus.AVERAGE_RESTART_OBSERVE_THRESHOLD) {
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
        val podStatus = findPodStatus(pods.mapNotNull { it.health })

        if (podStatus != HEALTHY) {
            return AuroraStatus(podStatus, "POD_HEALTH_CHECK")
        }

        if (availableReplicas == targetReplicas && availableReplicas != 0) {
            return AuroraStatus(HEALTHY)
        }

        return AuroraStatus(AuroraStatus.AuroraStatusLevel.UNKNOWN)
    }

    @JvmStatic
    fun findPodStatus(pods: List<JsonNode>): AuroraStatus.AuroraStatusLevel {
        return pods.map {
            val status = it.get("status").asText()
            AuroraStatus.fromApplicationStatus(status, "").level
        }.toSortedSet().firstOrNull() ?: UNKNOWN
    }

    fun findAverageRestarts(ap: List<PodDetails>): Int {
        if (ap.isEmpty()) {
            return 0
        }

        val totalRestarts=ap.sumBy { it.restartCount }
        return totalRestarts / ap.size
    }

    fun hasOldPodsWithDifferentDeployments(ap: List<PodDetails>, threshold: Instant): Boolean {
        if (ap.size < 2) {
            return false
        }

        val numberOfDifferentDeployments = ap.map { it.deployment }.distinct().count()
        if (numberOfDifferentDeployments == 1) {
            return false
        }

        return ap.stream().anyMatch { p -> Instant.parse(p.startTime).isBefore(threshold) }
    }
}