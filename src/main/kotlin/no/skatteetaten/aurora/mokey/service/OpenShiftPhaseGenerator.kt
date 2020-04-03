package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentCondition
import java.time.Duration
import java.time.Instant

//this must be here since the same class is in different packages. It is the same as in ApplicationDataServiceOpenshift

fun List<DeploymentCondition>.findOpenshiftPhase(scalingLimit: Duration, time: Instant): String? {
    val progressing = this.find { it.type == "Progressing" } ?: return null // TODO nodeploy

    val availabilityPhase = this.find { it.type == "Available" }?.findAvailableStatus(scalingLimit, time)

    val progressingStatus = progressing.findProgressingStatus()
    if (progressingStatus != "Complete") {
        return progressingStatus
    }

    return availabilityPhase
}

fun DeploymentCondition.findAvailableStatus(limit: Duration, time: Instant): String {

    if (this.status.toLowerCase() != "false") {
        return "Complete"
    }
    val updatedAt = Instant.parse(this.lastUpdateTime)
    val duration = Duration.between(updatedAt, time)
    return if (duration > limit) {
        // We have tried scaling over the limit
        // TODO: This should really be Scaling Timeout
        "Complete"
    } else {
        "Scaling"
    }
}

fun DeploymentCondition.findProgressingStatus(): String {
    if (this.status.toLowerCase() == "false") {
        return "Failed"
    }
    // TODO: double check this value
    return if (this.reason == "NewReplicaSetAvailable" || this.reason == "NewReplicationControllerAvailable") {
        "Complete"
    } else {
        "DeploymentProgressing"
    }
}
