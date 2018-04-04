package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.model.Endpoint
import java.time.Instant

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
        val buildInfo: BuildInfoResource?,
        val imageDetails: ImageDetailsResource?,
        val podResources: List<PodResource>,
        val dependencies: Map<String, String> = mapOf(),
        val serviceLinks: Map<String, String> = mapOf()
)

data class ImageDetailsResource(
        val dockerImageReference: String?/*,
        val environmentVariables: Map<String, String>*/
)

data class BuildInfoResource(
        val imageBuildTime: Instant?,
        val buildTime: Instant? = null,
        val commitId: String? = null,
        val commitTime: Instant? = null
)

data class ValueOrManagementError<V>(val value: V? = null, val error: ManagementEndpointErrorResource? = null)

data class PodResource(
//        val health: ValueOrManagementError<Map<String, Any>>,
//        val env: ValueOrManagementError<JsonNode>,
//        val podLinks: ValueOrManagementError<Map<String, String>>
        val podLinks: Map<String, String>?
)

data class ManagementEndpointErrorResource(
        val message: String,
        val endpoint: Endpoint,
        val code: String,
        val rootCause: String? = null
)