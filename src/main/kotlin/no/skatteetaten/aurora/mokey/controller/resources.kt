package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.skatteetaten.aurora.mokey.model.Endpoint
import org.springframework.hateoas.ResourceSupport
import java.time.Instant
import java.util.HashMap


abstract class HalResource : ResourceSupport() {

    private val embedded = HashMap<String, ResourceSupport>()

    val embeddedResources: Map<String, ResourceSupport>
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("_embedded")
        get() = embedded

    fun embedResource(relationship: String, resource: ResourceSupport) {

        embedded[relationship] = resource
    }
}


class ApplicationResource(
        val affiliation: String?,
        val environment: String,
        val name: String,
        val status: AuroraStatusResource,
        val version: Version
) : HalResource()

data class Version(val deployTag: String?, val auroraVersion: String?)

data class AuroraStatusResource(val code: String, val comment: String? = null)

class ApplicationDetailsResource(
        val buildInfo: BuildInfoResource?,
        val imageDetails: ImageDetailsResource?,
        val podResources: List<PodResource>,
        val dependencies: Map<String, String> = mapOf()
) : HalResource()

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