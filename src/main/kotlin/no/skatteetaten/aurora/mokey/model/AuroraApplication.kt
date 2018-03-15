package no.skatteetaten.aurora.mokey.model

import no.skatteetaten.aurora.mokey.controller.ImageDetails
import no.skatteetaten.aurora.mokey.controller.PodDetails

enum class AuroraStatusLevel(val level: Int) {
    DOWN(4),
    UNKNOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
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

data class ApplicationData(
    val name: String?,
    val namespace: String,
    val deployTag: String,
    val booberDeployId: String?,
    val affiliation: String,
    val targetReplicas: Int?,
    val availableReplicas: Int,
    val deploymentPhase: String?,
    val routeUrl: String?,
    val managementPath: String?,
    val pods: List<PodDetails>,
    val imageStream: ImageDetails?,
    val sprocketDone: String?,
    val violationRules: MutableSet<String>,
    val auroraVersion: String
)
