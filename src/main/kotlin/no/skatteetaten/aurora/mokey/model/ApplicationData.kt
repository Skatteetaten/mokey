package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.service.Endpoint
import no.skatteetaten.aurora.mokey.service.ManagementLinks
import no.skatteetaten.aurora.utils.Either

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
        val managementData: Either<ManagementEndpointError, ManagementData>
)


data class ManagementEndpointError(
        val message: String,
        val endpoint: Endpoint,
        val code: String,
        val rootCause: String? = null
)


data class ManagementData(
        val links: ManagementLinks? = null,
        val info: Either<ManagementEndpointError, JsonNode>,
        val health: Either<ManagementEndpointError, JsonNode>,
        val env: Either<ManagementEndpointError, JsonNode>
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

