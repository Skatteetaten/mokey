package no.skatteetaten.aurora.mokey.model

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

data class ApplicationData(
    val name: String,
    val namespace: String,
    val deployTag: String,
    val booberDeployId: String?,
    val affiliation: String,
    val targetReplicas: Int,
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
