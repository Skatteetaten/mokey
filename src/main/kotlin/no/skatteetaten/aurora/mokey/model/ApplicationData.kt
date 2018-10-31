package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.databind.JsonNode
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
    val dockerImageRepo: String? = null,
    val releaseTo: String?,
    val time: Instant = Instant.now()
)

data class ApplicationData(
    val booberDeployId: String? = null,
    val managementPath: String? = null,
    val pods: List<PodDetails> = emptyList(),
    val imageDetails: ImageDetails? = null,
    val deployDetails: DeployDetails? = null,
    val addresses: List<Address> = emptyList(),
    val sprocketDone: String? = null,
    val splunkIndex: String? = null,
    val deploymentCommand: ApplicationDeploymentCommand,
    val publicData: ApplicationPublicData

) {
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
    val managementData: ManagementData
)

data class ManagementEndpointError(
    val message: String,
    val endpointType: Endpoint,
    val code: String,
    val rootCause: String? = null,
    val url: String? = null
)

data class ManagementData(
    val links: ManagementEndpointResult<ManagementLinks>,
    val info: ManagementEndpointResult<InfoResponse>? = null,
    val health: ManagementEndpointResult<HealthResponse>? = null,
    val env: ManagementEndpointResult<JsonNode>? = null
)

data class ManagementEndpointResult<T>(
    val deserialized: T? = null,
    val textResponse: String,
    val createdAt: Instant = Instant.now(),
    val endpointType: Endpoint,
    val code: String,
    val rootCause: String? = null,
    val url: String? = null
) {
    val isSuccess: Boolean
        get() = code == "OK"
}

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