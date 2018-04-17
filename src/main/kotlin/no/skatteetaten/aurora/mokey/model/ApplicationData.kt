package no.skatteetaten.aurora.mokey.model

import no.skatteetaten.aurora.mokey.controller.ManagementEndpointErrorResource
import no.skatteetaten.aurora.mokey.controller.ValueOrManagementError
import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.error
import no.skatteetaten.aurora.utils.fold
import no.skatteetaten.aurora.utils.value
import java.time.Instant

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
        val addresses: List<Address>,
        val sprocketDone: String? = null
) {
    val errors
        get(): List<ManagementEndpointError> = this.pods
                .map { it.managementData }
                .flatMap { listOf(it, it.value?.info, it.value?.health).map { it?.error } }
                .filterNotNull()
}

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
        val info: Either<ManagementEndpointError, InfoResponse>,
        val health: Either<ManagementEndpointError, HealthResponse>/*,
        val env: Either<ManagementEndpointError, JsonNode>*/
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
        val imageBuildTime: Instant?,
        val environmentVariables: Map<String, String>
) {
    val auroraVersion: String
        get() = environmentVariables["AURORA_VERSION"] ?: ""
}

data class DeployDetails(val deploymentPhase: String?, val availableReplicas: Int, val targetReplicas: Int)

fun <F, T> mappedValueOrError(either: Either<ManagementEndpointError, F>, valueMapper: (from: F) -> T): ValueOrManagementError<T> =
        either.fold(
                { it: ManagementEndpointError ->
                    ValueOrManagementError(error = ({ e: ManagementEndpointError ->
                        ManagementEndpointErrorResource(
                                message = e.message,
                                code = e.code,
                                endpoint = e.endpoint,
                                rootCause = e.rootCause
                        )
                    })(it))
                },
                { ValueOrManagementError(valueMapper.invoke(it)) }
        )
