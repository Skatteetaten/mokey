package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.service.Endpoint
import no.skatteetaten.aurora.mokey.service.ManagementLinks

data class ApplicationData(
        val id: String,
        val auroraStatus: AuroraStatus,
        val deployTag: String,
        val name: String,
        val namespace: String,
        val affiliation: String?,
        val booberDeployId: String? = null,
        val managementPath: String? = null,
        val pods: List<PodDetails> = emptyList(),
        val imageDetails: ImageDetails? = null,
        val deployDetails: DeployDetails,
        val sprocketDone: String? = null
)

data class PodDetails(
        val openShiftPodExcerpt: OpenShiftPodExcerpt,
        val managementData: ManagementResult<ManagementData>
)

data class ManagementEndpointError(
        val message: String,
        val endpoint: Endpoint,
        val code: String,
        val rootCause: String? = null
)

data class ManagementResult<out T>(val value: T? = null, val error: ManagementEndpointError? = null)
data class ManagementData constructor(
        val links: ManagementLinks? = null,
        val info: ManagementResult<JsonNode>,
        val health: ManagementResult<JsonNode>
)


data class OpenShiftPodExcerpt(
        val name: String,
        val status: String,
        val restartCount: Int = 0,
        val ready: Boolean = false,
        val podIP: String?,
        val startTime: String,
        val deployment: String?
)

data class ImageDetails(
        val dockerImageReference: String?,
        val environmentVariables: Map<String, String>
) {
    val auroraVersion: String
        get() = environmentVariables["AURORA_VERSION"] ?: ""

    val imageBuildTime: String
        get() = environmentVariables["IMAGE_BUILD_TIME"] ?: ""
}

data class DeployDetails(val deploymentPhase: String?, val availableReplicas: Int, val targetReplicas: Int)