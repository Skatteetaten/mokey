package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel

data class AuroraApplication(
    val id: ApplicationId,
    val status: String,
    val version: Version
)

data class ApplicationId(val name: String, val environment: Environment)
data class Environment(val name: String, val affiliation: String) {
    companion object {
        fun fromNamespace(namespace: String): Environment {
            val affiliation = namespace.substringBefore("-")
            val name = namespace.substringAfter("-")
            return Environment(name, affiliation)
        }
    }
}

data class Version(val deployTag: String?, val auroraVersion: String?)
data class ApplicationData(
    val application: AuroraApplication,
    val auroraStatus: AuroraStatus,
    val pods: List<PodDetails> = listOf(),
    val imageDetails: ImageDetails? = null,
    val deployment: OpenShiftDeploymentExcerpt,

    val routeUrl: String? = null
)

data class AuroraStatus(val level: AuroraStatusLevel, val comment: String)
data class PodDetails(
    val openShiftPodExcerpt: OpenShiftPodExcerpt,
    val links: Map<String, String>? = null
)

data class OpenShiftPodExcerpt(
    val name: String,
    val status: String,
    val restartCount: Int = 0,
    val ready: Boolean = false,
    val podIP: String,
    val startTime: String,
    val deployment: String?
)

data class ImageDetails(
    val buildTime: String,

    val registryUrl: String,
    val group: String,
    val name: String,
    val tag: String,
    val env: Map<String, String>? = null
)

data class OpenShiftDeploymentExcerpt(
    val booberDeployId: String? = null,
    val managementPath: String? = null,
    val sprocketDone: String? = null,
    val targetReplicas: Int = 0,
    val availableReplicas: Int = 0,
    val deploymentPhase: String? = null
)