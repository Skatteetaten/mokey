package no.skatteetaten.aurora.mokey.model

import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.error
import no.skatteetaten.aurora.utils.value
import java.time.Instant

data class GroupedApplicationData(
    val applicationId: String?,
    val name: String,
    val applications: List<ApplicationData>
) {
    constructor(application: ApplicationData) : this(application.applicationId, application.name, listOf(application))

    companion object {
        fun create(applications: List<ApplicationData>): List<GroupedApplicationData> =
            applications.groupBy { it.applicationId ?: it.name }
                .map { GroupedApplicationData(it.value.first().applicationId, it.value.first().name, it.value) }
    }
}

data class ApplicationData(
    val applicationId: String?,
    val applicationInstanceId: String,
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
    val sprocketDone: String? = null,
    val splunkIndex: String? = null
) {
    val errors
        get(): List<PodError> = this.pods
            .flatMap { podDetails: PodDetails ->
                val data = podDetails.managementData
                listOf(data, data.value?.info, data.value?.health).map { it?.error?.let { PodError(podDetails, it) } }
            }
            .filterNotNull()
}

data class PodDetails(
    val openShiftPodExcerpt: OpenShiftPodExcerpt,
    val managementData: Either<ManagementEndpointError, ManagementData>
)

data class PodError(
    val podDetails: PodDetails,
    val error: ManagementEndpointError
)

data class ManagementEndpointError(
    val message: String,
    val endpointType: Endpoint,
    val code: String,
    val rootCause: String? = null,
    val url: String? = null
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
    val startTime: String?,
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