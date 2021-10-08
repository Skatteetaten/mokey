package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentCondition
import java.time.Duration
import java.time.Instant
import java.util.Locale.getDefault

// this must be here since the same class is in different packages. It is the same as in ApplicationDataServiceOpenshift

@Suppress("DuplicatedCode")
fun List<DeploymentCondition>.findOpenshiftPhase(scalingLimit: Duration, time: Instant): String? {
    val progressing = this.find { it.type == "Progressing" } ?: return null // TODO nodeploy
    val availabilityPhase = this.find { it.type == "Available" }?.findAvailableStatus(scalingLimit, time)
    val progressingStatus = progressing.findProgressingStatus()

    if (progressingStatus != "Complete") return progressingStatus

    return availabilityPhase
}

@Suppress("DuplicatedCode")
fun DeploymentCondition.findAvailableStatus(limit: Duration, time: Instant): String {
    if (this.status.lowercase(getDefault()) != "false") return "Complete"

    val updatedAt = Instant.parse(this.lastUpdateTime)
    val duration = Duration.between(updatedAt, time)

    return when {
        duration > limit -> "Complete"
        else -> "Scaling"
    }
}

@Suppress("DuplicatedCode")
fun DeploymentCondition.findProgressingStatus(): String {
    if (this.status.lowercase(getDefault()) == "false") return "Failed"

    return when (this.reason) {
        "NewReplicaSetAvailable", "NewReplicationControllerAvailable" -> "Complete"
        else -> "DeploymentProgressing"
    }
}
