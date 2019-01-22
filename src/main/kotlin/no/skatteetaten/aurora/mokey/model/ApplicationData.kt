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
    val time: Instant = Instant.now(),
    val paused: Boolean = false,
    val message: String? = null
)

data class ApplicationData(
    val booberDeployId: String? = null,
    val managementPath: String? = null,
    val pods: List<PodDetails> = emptyList(),
    val databaseDetails: DatabaseDetails? = null,
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

data class DatabaseDetails(val id: String)

data class PodDetails(
    val openShiftPodExcerpt: OpenShiftPodExcerpt,
    val managementData: ManagementData
)

data class ManagementData(
    val links: ManagementEndpointResult<ManagementLinks>,
    val info: ManagementEndpointResult<InfoResponse>? = null,
    val health: ManagementEndpointResult<HealthResponse>? = null,
    val env: ManagementEndpointResult<JsonNode>? = null
)

data class ManagementEndpointResult<T>(
    val endpointType: EndpointType,
    val resultCode: String,
    val createdAt: Instant = Instant.now(),
    val deserialized: T? = null,
    val response: HttpResponse? = null,
    val errorMessage: String? = null,
    val url: String? = null
) {
    val isSuccess: Boolean
        get() = resultCode == "OK"
}

data class HttpResponse(
    val content: String,
    val code: Int
)

data class OpenShiftPodExcerpt(
    val name: String,
    val phase: String,
    val podIP: String?,
    val startTime: String?,
    val replicaName: String?,
    val deployTag: String?,
    val containers: List<OpenShiftContainerExcerpt>,
    val latestDeployTag: Boolean,
    val latestReplicaName: Boolean
)

data class OpenShiftContainerExcerpt(
    val name: String,
    val state: String,
    val image: String,
    val restartCount: Int = 0,
    val ready: Boolean = false
)

data class ImageDetails(
    val dockerImageReference: String,
    val dockerImageTagReference: String?,
    val imageBuildTime: Instant?,
    val environmentVariables: Map<String, String>
) {
    val auroraVersion: String
        get() = environmentVariables["AURORA_VERSION"] ?: ""
    val dockerImageRepo: String?
        get() = dockerImageReference.replace(Regex("@.*$"), "")
}

data class DeployDetails(
    val targetReplicas: Int,
    val availableReplicas: Int,
    val deployment: String? = null,
    val phase: String? = null,
    val deployTag: String? = null,
    val paused: Boolean = false
) {
    val lastDeployment: String?
        get() = this.phase?.toLowerCase()
}
