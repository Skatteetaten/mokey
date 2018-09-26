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
import no.skatteetaten.aurora.utils.value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class AuroraStatusCalculator {

    // This is a service because these should be loaded from application.yaml Create ConfigurationClass
    val AVERAGE_RESTART_OBSERVE_THRESHOLD = 20
    val AVERAGE_RESTART_ERROR_THRESHOLD = 100
    val DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD = 2

    fun calculateStatus(app: DeployDetails, pods: List<PodDetails>): AuroraStatus {

        val lastDeployment = app.deploymentPhase
        val availableReplicas = app.availableReplicas
        val targetReplicas = app.targetReplicas

        val healthStatuses = mutableListOf<HealthStatusDetail>()

        val threshold = Instant.now().minus(Duration.ofHours(DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD.toLong()))
        if (hasOldPodsWithDifferentDeployments(pods, threshold)) {
            healthStatuses.add(HealthStatusDetail(DOWN, "DIFFERENT_DEPLOYMENTS"))
        }

        if (targetReplicas > availableReplicas && availableReplicas == 0) {
            healthStatuses.add(HealthStatusDetail(DOWN, "NO_AVAILABLE_PODS"))
        }

        if ("Failed".equals(lastDeployment, ignoreCase = true) && availableReplicas <= 0) {
            healthStatuses.add(HealthStatusDetail(DOWN, "DEPLOY_FAILED_NO_PODS"))
        }

        val averageRestarts = findAverageRestarts(pods)

        if (averageRestarts > AVERAGE_RESTART_ERROR_THRESHOLD) {
            healthStatuses.add(
                HealthStatusDetail(
                    DOWN,
                    "AVERAGE_RESTART_ABOVE_THRESHOLD"
                )
            )
        }

        if (averageRestarts > AVERAGE_RESTART_OBSERVE_THRESHOLD) {
            healthStatuses.add(
                HealthStatusDetail(
                    OBSERVE,
                    "AVERAGE_RESTART_ABOVE_THRESHOLD"
                )
            )
        }

        if ("Failed".equals(lastDeployment, ignoreCase = true)) {
            healthStatuses.add(HealthStatusDetail(OBSERVE, "DEPLOY_FAILED"))
        }

        if (targetReplicas < availableReplicas) {
            healthStatuses.add(HealthStatusDetail(OBSERVE, "TOO_FEW_PODS"))
        }

        if (targetReplicas > availableReplicas && availableReplicas != 0) {
            healthStatuses.add(HealthStatusDetail(OBSERVE, "TOO_MANY_PODS"))
        }

        if (targetReplicas == 0 && availableReplicas == 0) {
            healthStatuses.add(HealthStatusDetail(OFF, "OFF"))
        }

        if (lastDeployment == null) {
            healthStatuses.add(HealthStatusDetail(OFF, "NO_DEPLOYMENT"))
        }

        healthStatuses.addAll(findPodStatuses(pods))

        return findMostCriticalStatus(healthStatuses)
    }

    fun findPodStatuses(pods: List<PodDetails>): List<HealthStatusDetail> {
        return pods.mapNotNull { pod ->
            pod.managementData.value?.health?.value?.let {
                val statusLevel = fromApplicationStatus(it.deserialized.status.name)
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

    private fun findMostCriticalStatus(healthStatuses: List<HealthStatusDetail>): AuroraStatus {

        val getMostCriticalStatus = { acc: HealthStatusDetail, auroraStatus: HealthStatusDetail ->
            if (auroraStatus.level.level > acc.level.level) auroraStatus else acc
        }

        val nonHealthyStatuses = healthStatuses.filter { it.level !== HEALTHY }

        return when {
            nonHealthyStatuses.isEmpty() -> AuroraStatus(HEALTHY, "", healthStatuses)
            else -> nonHealthyStatuses.reduce(getMostCriticalStatus).let {
                AuroraStatus(it.level, it.comment, healthStatuses)
            }
        }
    }
}