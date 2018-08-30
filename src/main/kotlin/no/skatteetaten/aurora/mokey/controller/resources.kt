package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import no.skatteetaten.aurora.mokey.model.Endpoint
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
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
    val appId: String?,
    val name: String,
    val tags: List<String>,
    val applicationDeployments: List<ApplicationDeploymentResource>
) : HalResource()

data class ApplicationDeploymentResource(
    val affiliation: String?,
    val environment: String,
    val namespace: String,
    val status: AuroraStatusResource,
    val version: Version
) : HalResource()

data class Version(val deployTag: String?, val auroraVersion: String?)

data class AuroraStatusResource(
    val code: String,
    val comment: String? = null,
    val details: List<HealthStatusDetailResource>? = null
)

data class HealthStatusDetailResource(val code: String, val comment: String, val ref: String?)

class ApplicationDeploymentDetailsResource(
    val buildTime: Instant? = null,
    val gitInfo: GitInfoResource?,
    val imageDetails: ImageDetailsResource?,
    val podResources: List<PodResource>,
    val dependencies: Map<String, String> = emptyMap(),

    val errors: List<ManagementEndpointErrorResource> = emptyList()
) : HalResource()

data class ImageDetailsResource(
    val imageBuildTime: Instant?,
    val dockerImageReference: String?
)

data class GitInfoResource(
    val commitId: String? = null,
    val commitTime: Instant? = null
)

class PodResource(
    val name: String,
    val status: String,
    val restartCount: Int,
    val ready: Boolean,
    val startTime: String?
) : ResourceSupport()

data class ManagementEndpointErrorResource(
    val podName: String,
    val message: String,
    val endpoint: Endpoint,
    val url: String?,
    val code: String,
    val rootCause: String? = null
) {
    val type: String = ManagementEndpointError::class.simpleName!!
}