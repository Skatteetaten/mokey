package no.skatteetaten.aurora.mokey.model

import no.skatteetaten.aurora.utils.value
import java.time.Duration
import java.time.Instant

class AuroraStatus {

    val AVERAGE_RESTART_OBSERVE_THRESHOLD = 20
    val AVERAGE_RESTART_ERROR_THRESHOLD = 100
    val DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD = 2

    val level: AuroraStatusLevel
    val comment: String
    val statuses: List<HealthStatusDetail>

    constructor(level: AuroraStatusLevel, comment: String, statuses: List<HealthStatusDetail>) {
        this.level = level
        this.comment = comment
        this.statuses = statuses
    }

    constructor(app: DeployDetails, pods: List<PodDetails>) {
        val lastDeployment = app.deploymentPhase
        val availableReplicas = app.availableReplicas
        val targetReplicas = app.targetReplicas
        val healthStatuses = mutableListOf<HealthStatusDetail>()
        val threshold = Instant.now().minus(Duration.ofHours(DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD.toLong()))
        if (hasOldPodsWithDifferentDeployments(pods, threshold)) {
            healthStatuses.add(HealthStatusDetail(AuroraStatusLevel.DOWN, "DIFFERENT_DEPLOYMENTS"))
        }
        if (targetReplicas > availableReplicas && availableReplicas == 0) {
            healthStatuses.add(HealthStatusDetail(AuroraStatusLevel.DOWN, "NO_AVAILABLE_PODS"))
        }
        if ("Failed".equals(lastDeployment, ignoreCase = true) && availableReplicas <= 0) {
            healthStatuses.add(HealthStatusDetail(AuroraStatusLevel.DOWN, "DEPLOY_FAILED_NO_PODS"))
        }
        val averageRestarts = findAverageRestarts(pods)
        if (averageRestarts > AVERAGE_RESTART_ERROR_THRESHOLD) {
            healthStatuses.add(
                    HealthStatusDetail(
                            AuroraStatusLevel.DOWN,
                            "AVERAGE_RESTART_ABOVE_THRESHOLD"
                    )
            )
        }
        if (averageRestarts > AVERAGE_RESTART_OBSERVE_THRESHOLD) {
            healthStatuses.add(
                    HealthStatusDetail(
                            AuroraStatusLevel.OBSERVE,
                            "AVERAGE_RESTART_ABOVE_THRESHOLD"
                    )
            )
        }
        if ("Failed".equals(lastDeployment, ignoreCase = true)) {
            healthStatuses.add(HealthStatusDetail(AuroraStatusLevel.OBSERVE, "DEPLOY_FAILED"))
        }
        if (targetReplicas < availableReplicas) {
            healthStatuses.add(HealthStatusDetail(AuroraStatusLevel.OBSERVE, "TOO_FEW_PODS"))
        }
        if (targetReplicas > availableReplicas && availableReplicas != 0) {
            healthStatuses.add(HealthStatusDetail(AuroraStatusLevel.OBSERVE, "TOO_MANY_PODS"))
        }
        if (targetReplicas == 0 && availableReplicas == 0) {
            healthStatuses.add(HealthStatusDetail(AuroraStatusLevel.OFF, "OFF"))
        }
        if (lastDeployment == null) {
            healthStatuses.add(HealthStatusDetail(AuroraStatusLevel.OFF, "NO_DEPLOYMENT"))
        }
        healthStatuses.addAll(findPodStatuses(pods))
        val nonHealthyStatuses = healthStatuses.filter { it.level !== AuroraStatusLevel.HEALTHY }
        if (nonHealthyStatuses.isEmpty()) {
            level = AuroraStatusLevel.HEALTHY
            comment = ""
            statuses = healthStatuses
        } else {
            val mostCriticalStatus = nonHealthyStatuses.maxBy { it.level.level } ?: throw IllegalArgumentException("")
            level = mostCriticalStatus.level
            comment = mostCriticalStatus.comment
            statuses = healthStatuses
        }
    }


    fun findPodStatuses(pods: List<PodDetails>): List<HealthStatusDetail> {
        return pods.mapNotNull { pod ->
            pod.managementData.value?.health?.value?.let {
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


// data class AuroraStatus(val level: AuroraStatusLevel, val comment: String, val statuses: List<HealthStatusDetail>)

enum class AuroraStatusLevel(val level: Int) {
    DOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
}

data class HealthStatusDetail(val level: AuroraStatusLevel, val comment: String, val ref: String? = null)
