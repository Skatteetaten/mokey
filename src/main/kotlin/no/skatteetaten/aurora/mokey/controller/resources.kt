package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.service.Endpoint
import no.skatteetaten.aurora.mokey.service.HealthResponse

open class ValueOrError<V, E>(val value: V?, val error: E?)

data class ApplicationResource(
        val id: String,
        val affiliation: String?,
        val environment: String,
        val name: String,
        val status: AuroraStatusResource,
        val version: Version
)

data class Version(val deployTag: String?, val auroraVersion: String?)

data class AuroraStatusResource(val code: String, val comment: String? = null)

data class ApplicationDetailsResource(
        val application: ApplicationResource,
        val imageDetails: ImageDetailsResource,
        val podResources: List<PodResource>
)

data class ImageDetailsResource(
        val dockerImageReference: String?,
        val imageBuildTime: String?,
        val environmentVariables: Map<String, String>?
)

class ValueOrManagementError<V>(value: V? = null, error: ManagementEndpointErrorResource? = null) : ValueOrError<V, ManagementEndpointErrorResource>(value, error)

data class PodResource(val managementData: ValueOrManagementError<ManagementDataResource>)

data class ManagementDataResource(
        val info: ValueOrManagementError<JsonNode>,
        val health: ValueOrManagementError<HealthResponse>,
        val env: ValueOrManagementError<JsonNode>
)

data class ManagementEndpointErrorResource(
        val message: String,
        val endpoint: Endpoint,
        val code: String,
        val rootCause: String? = null
)