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
    val applicationInstances: List<ApplicationInstanceResource>
) : HalResource()

data class ApplicationInstanceResource(
    val affiliation: String?,
    val environment: String,
    val namespace: String,
    val status: StatusResource,
    val version: Version
) : HalResource()

data class Version(val deployTag: String?, val auroraVersion: String?)

data class StatusResource(
    val code: String,
    val comment: String? = null,
    val details: AuroraStatusDetailsResource? = null
)

data class AuroraStatusResource(val code: String, val comment: String? = null)

data class AuroraStatusDetailsResource(
    val deploymentStatuses: List<AuroraStatusResource>,
    val podStatuses: Map<String, AuroraStatusResource>
)

class ApplicationInstanceDetailsResource(
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