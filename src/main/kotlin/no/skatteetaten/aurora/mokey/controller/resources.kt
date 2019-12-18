package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import kotlin.reflect.KClass
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import uk.q3c.rest.hal.HalResource
import uk.q3c.rest.hal.Links

abstract class IdentifiedHalResource(val identifier: String?) : HalResource()

class ApplicationResource(
    id: String?,
    val name: String,
    val applicationDeployments: List<ApplicationDeploymentResource>
) : IdentifiedHalResource(id)

class ApplicationDeploymentResource(
    id: String?,
    val affiliation: String?,
    val environment: String,
    val namespace: String,
    val name: String,
    val status: AuroraStatusResource,
    val version: Version,
    val dockerImageRepo: String?,
    val time: Instant,
    val message: String?
) : IdentifiedHalResource(id)

class ApplicationDeploymentsWithDbResource(
    databaseId: String?,
    val applicationDeployments: List<ApplicationDeploymentResource>
) : IdentifiedHalResource(databaseId)

data class Version(val deployTag: String?, val auroraVersion: String?, val releaseTo: String?)

data class AuroraStatusResource(
    val code: String,
    val comment: String? = null,
    val reasons: List<StatusCheckReportResource> = listOf(),
    val reports: List<StatusCheckReportResource> = listOf()
)

data class StatusCheckReportResource(
    val name: String,
    val description: String,
    val failLevel: AuroraStatusLevel,
    val hasFailed: Boolean
)

class ApplicationDeploymentDetailsResource(
    id: String?,
    val updatedBy: String?,
    val buildTime: Instant? = null,
    val gitInfo: GitInfoResource?,
    val imageDetails: ImageDetailsResource?,
    val podResources: List<PodResource>,
    val databases: List<String>,
    val dependencies: Map<String, String> = emptyMap(),
    val applicationDeploymentCommand: ApplicationDeploymentCommandResource,
    val deployDetails: DeployDetailsResource?,
    val serviceLinks: Links
) : IdentifiedHalResource(id)

data class ImageDetailsResource(
    val imageBuildTime: Instant?,
    val dockerImageReference: String?,
    val dockerImageTagReference: String?
)

data class GitInfoResource(
    val commitId: String? = null,
    val commitTime: Instant? = null
)

class PodResource(
    val name: String,
    val phase: String,
    val startTime: String?,
    val replicaName: String?,
    val latestReplicaName: Boolean,
    val managementResponses: ManagementResponsesResource?,
    val containers: List<ContainerResource>,
    val deployTag: String?,
    val latestDeployTag: Boolean,
    // TODO: remove after AOS-3026 is deployed
    val status: String = phase,
    val ready: Boolean = containers.all { it.ready },
    val restartCount: Int = containers.sumBy { it.restartCount }
) : HalResource()

data class DeployDetailsResource(
    val targetReplicas: Int,
    val availableReplicas: Int,
    val deployment: String? = null,
    val phase: String? = null,
    val deployTag: String? = null,
    val paused: Boolean
)

data class ContainerResource(
    val name: String,
    val state: String,
    val image: String,
    val restartCount: Int = 0,
    val ready: Boolean = false
)

data class HttpResponseResource(
    val hasResponse: Boolean,
    val textResponse: String? = null,
    val httpCode: Int? = null,
    val createdAt: Instant = Instant.now(),
    val url: String? = null,
    val error: ManagementEndpointErrorResource? = null
)

data class ManagementResponsesResource(
    val links: HttpResponseResource,
    val health: HttpResponseResource?,
    val info: HttpResponseResource?,
    val env: HttpResponseResource?
)

data class ApplicationDeploymentCommandResource(
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val overrideFiles: Map<String, String> = emptyMap(),
    val applicationDeploymentRef: ApplicationDeploymentRefResource,
    val auroraConfig: AuroraConfigRefResource
)

data class ApplicationDeploymentRefResource(val environment: String, val application: String)

data class AuroraConfigRefResource(
    val name: String,
    val refName: String,
    val resolvedRef: String? = null
)

data class ManagementEndpointErrorResource(
    val code: String,
    val message: String? = null
)

fun <T : HalResource> resourceClassNameToRelName(kClass: KClass<T>): String =
    kClass.simpleName!!.replace("Resource$".toRegex(), "")
