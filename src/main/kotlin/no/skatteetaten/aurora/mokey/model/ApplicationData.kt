package no.skatteetaten.aurora.mokey.model

import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.error
import no.skatteetaten.aurora.utils.value
import java.time.Instant

data class GroupedApplicationData(
    val applicationId: String?,
    val name: String,
    val applications: List<ApplicationPublicData>
) {
    constructor(application: ApplicationPublicData) : this(
        application.applicationId,
        application.applicationName,
        listOf(application)
    )

    companion object {
        fun create(applications: List<ApplicationPublicData>): List<GroupedApplicationData> =
            applications.groupBy { it.applicationId ?: it.applicationName }
                .map {
                    GroupedApplicationData(
                        it.value.first().applicationId,
                        it.value.first().applicationName,
                        it.value
                    )
                }
    }
}

data class ApplicationPublicData(
    val applicationId: String?,
    val applicationDeploymentId: String,
    val applicationName: String,
    val applicationDeploymentName: String,
    val auroraStatus: AuroraStatus,
    val affiliation: String?,
    val namespace: String,
    val deployTag: String,
    val auroraVersion: String? = null,
    val dockerImageRepo: String?
)

data class ApplicationData(
    val booberDeployId: String? = null,
    val managementPath: String? = null,
    val pods: List<PodDetails> = emptyList(),
    val imageDetails: ImageDetails? = null,
    val deployDetails: DeployDetails,
    val addresses: List<Address>,
    val sprocketDone: String? = null,
    val splunkIndex: String? = null,
    val deploymentCommand: ApplicationDeploymentCommand,
    val publicData: ApplicationPublicData
) {
    val errors
        get(): List<PodError> = this.pods
            .flatMap { podDetails: PodDetails ->
                val data = podDetails.managementData
                listOf(data, data.value?.info, data.value?.health).map { it?.error?.let { PodError(podDetails, it) } }
            }
            .filterNotNull()

    val applicationId get() = publicData.applicationId
    val applicationDeploymentId get() = publicData.applicationDeploymentId
    val applicationName get() = publicData.applicationName
    val applicationDeploymentName get() = publicData.applicationDeploymentName
    val auroraStatus get() = publicData.auroraStatus
    val affiliation get() = publicData.affiliation
    val namespace get() = publicData.namespace
    val deployTag get() = publicData.deployTag
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
    val info: Either<ManagementEndpointError, HttpResponse<InfoResponse>>,
    val health: Either<ManagementEndpointError, HttpResponse<HealthResponse>>/*,
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
    val dockerImageRepo: String?
        get() = dockerImageReference?.replace(Regex("@.*$"), "")
}

data class DeployDetails(val deploymentPhase: String?, val availableReplicas: Int, val targetReplicas: Int)