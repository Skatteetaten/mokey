package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.Endpoint
import org.springframework.hateoas.ResourceSupport
import java.time.Instant

class ApplicationResource(
        val id: String,
        val affiliation: String?,
        val environment: String,
        val name: String,
        val status: AuroraStatusResource,
        val version: Version
) : ResourceSupport()

data class Version(val deployTag: String?, val auroraVersion: String?)

data class AuroraStatusResource(val code: String, val comment: String? = null)

class ApplicationDetailsResource(
        val application: ApplicationResource,
        val buildInfo: BuildInfoResource?,
        val imageDetails: ImageDetailsResource?,
        val podResources: List<PodResource>,
        val dependencies: Map<String, String> = mapOf()
) : ResourceSupport()

data class ImageDetailsResource(
        val dockerImageReference: String?
)

data class BuildInfoResource(
        val imageBuildTime: Instant?,
        val buildTime: Instant? = null,
        val commitId: String? = null,
        val commitTime: Instant? = null
)

data class ValueOrManagementError<V>(val value: V? = null, val error: ManagementEndpointErrorResource? = null)

class PodResource(
        val name: String
) : ResourceSupport()

data class ManagementEndpointErrorResource(
        val message: String,
        val endpoint: Endpoint,
        val code: String,
        val rootCause: String? = null
)